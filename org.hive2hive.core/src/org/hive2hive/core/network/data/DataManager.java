package org.hive2hive.core.network.data;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.SignatureException;

import net.tomp2p.futures.FutureGet;
import net.tomp2p.futures.FuturePut;
import net.tomp2p.futures.FutureRemove;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.builder.DigestBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.Number640;
import net.tomp2p.storage.Data;

import org.hive2hive.core.H2HConstants;
import org.hive2hive.core.log.H2HLogger;
import org.hive2hive.core.log.H2HLoggerFactory;
import org.hive2hive.core.network.NetworkManager;
import org.hive2hive.core.network.data.futures.FutureGetListener;
import org.hive2hive.core.network.data.futures.FuturePutListener;
import org.hive2hive.core.network.data.futures.FutureRemoveListener;
import org.hive2hive.core.network.data.listener.IGetListener;
import org.hive2hive.core.network.data.listener.IPutListener;
import org.hive2hive.core.network.data.listener.IRemoveListener;

/**
 * This class offers an interface for putting, getting and removing data from the network.
 * 
 * @author Seppi
 */
public class DataManager {

	private static final H2HLogger logger = H2HLoggerFactory.getLogger(DataManager.class);

	private final NetworkManager networkManager;

	public DataManager(NetworkManager networkManager) {
		this.networkManager = networkManager;
	}

	/**
	 * Helper to get the <code>TomP2P</code> peer.
	 * 
	 * @return the current peer
	 */
	private Peer getPeer() {
		return networkManager.getConnection().getPeer();
	}

	public void put(String locationKey, String contentKey, NetworkContent content, KeyPair protectionKey,
			IPutListener listener) {
		Number160 lKey = Number160.createHash(locationKey);
		Number160 dKey = H2HConstants.TOMP2P_DEFAULT_KEY;
		Number160 cKey = Number160.createHash(contentKey);
		FuturePut putFuture = put(lKey, dKey, cKey, content, protectionKey);
		if (putFuture == null) {
			if (listener != null)
				listener.onPutFailure();
			return;
		}

		putFuture
				.addListener(new FuturePutListener(lKey, dKey, cKey, content, protectionKey, listener, this));
	}

	public void putUserProfileTask(String userId, Number160 contentKey, NetworkContent content,
			KeyPair protectionKey, IPutListener listener) {
		Number160 lKey = Number160.createHash(userId);
		Number160 dKey = Number160.createHash(H2HConstants.USER_PROFILE_TASK_DOMAIN);
		FuturePut putFuture = put(lKey, dKey, contentKey, content, protectionKey);
		if (putFuture == null) {
			if (listener != null)
				listener.onPutFailure();
			return;
		}

		putFuture.addListener(new FuturePutListener(lKey, dKey, contentKey, content, protectionKey, listener,
				this));
	}

	public FuturePut put(Number160 locationKey, Number160 domainKey, Number160 contentKey,
			NetworkContent content, KeyPair protectionKey) {
		logger.debug(String
				.format("put content = '%s' location key = '%s' domain key = '%s' content key = '%s' version key = '%s'",
						content.getClass().getName(), locationKey, domainKey, contentKey,
						content.getVersionKey()));
		try {
			Data data = new Data(content);
			data.ttlSeconds(content.getTimeToLive()).basedOn(content.getBasedOnKey());
			if (protectionKey != null) {
				data.setProtectedEntry().sign(protectionKey);
				return getPeer().put(locationKey).setData(contentKey, data).setDomainKey(domainKey)
						.setVersionKey(content.getVersionKey()).keyPair(protectionKey).start();
			} else {
				return getPeer().put(locationKey).setData(contentKey, data).setDomainKey(domainKey)
						.setVersionKey(content.getVersionKey()).start();
			}
		} catch (IOException | InvalidKeyException | SignatureException e) {
			logger.error(String
					.format("Put failed. location key = '%s' domain key = '%s' content key = '%s' version key = '%s' exception = '%s'",
							locationKey, domainKey, contentKey, content.getVersionKey(), e.getMessage()));
			return null;
		}
	}

	@Deprecated
	public void putLocal(String locationKey, String contentKey, NetworkContent content) {
		logger.debug(String.format("local put key = '%s' content key = '%s'", locationKey, contentKey));
		try {
			Number640 key = new Number640(Number160.createHash(locationKey), H2HConstants.TOMP2P_DEFAULT_KEY,
					Number160.createHash(contentKey), content.getVersionKey());
			Data data = new Data(content);
			data.ttlSeconds(content.getTimeToLive()).basedOn(content.getBasedOnKey());
			// TODO add public key for content protection
			getPeer().getPeerBean().storage().put(key, data, null, false, false);
		} catch (IOException e) {
			logger.error(String.format(
					"Local put failed. location key = '%s' content key = '%s' exception = '%s'", locationKey,
					contentKey, e.getMessage()));
		}
	}

	public void get(String locationKey, String contentKey, IGetListener listener) {
		Number160 lKey = Number160.createHash(locationKey);
		Number160 dKey = H2HConstants.TOMP2P_DEFAULT_KEY;
		Number160 cKey = Number160.createHash(contentKey);
		FutureGet futureGet = get(lKey, dKey, cKey);
		futureGet.addListener(new FutureGetListener(lKey, dKey, cKey, this, listener));
	}

	public void get(String locationKey, String contentKey, Number160 versionKey, IGetListener listener) {
		Number160 lKey = Number160.createHash(locationKey);
		Number160 dKey = H2HConstants.TOMP2P_DEFAULT_KEY;
		Number160 cKey = Number160.createHash(contentKey);
		FutureGet futureGet = get(lKey, dKey, cKey, versionKey);
		futureGet.addListener(new FutureGetListener(lKey, dKey, cKey, versionKey, this, listener));
	}

	public void getUserProfileTask(String userId, IGetListener listener) {
		Number160 lKey = Number160.createHash(userId);
		Number160 dKey = Number160.createHash(H2HConstants.USER_PROFILE_TASK_DOMAIN);
		FutureGet futureGet = getPeer().get(lKey)
				.from(new Number640(lKey, dKey, Number160.ZERO, Number160.ZERO))
				.to(new Number640(lKey, dKey, Number160.MAX_VALUE, Number160.MAX_VALUE)).ascending()
				.returnNr(1).start();
		futureGet.addListener(new FutureGetListener(lKey, dKey, this, listener));
	}

	public FutureGet get(Number160 locationKey, Number160 domainKey, Number160 contentKey) {
		logger.debug(String.format("get location key = '%s' domain key = '%s' content key = '%s'",
				locationKey, domainKey, contentKey));
		return getPeer().get(locationKey)
				.from(new Number640(locationKey, domainKey, contentKey, Number160.ZERO))
				.to(new Number640(locationKey, domainKey, contentKey, Number160.MAX_VALUE)).descending()
				.returnNr(1).start();
	}

	public FutureGet get(Number160 locationKey, Number160 domainKey, Number160 contentKey,
			Number160 versionKey) {
		logger.debug(String.format(
				"get location key = '%s' domain Key = '%s' content key = '%s' version key = '%s'",
				locationKey, domainKey, contentKey, versionKey));
		return getPeer().get(locationKey).setDomainKey(domainKey).setContentKey(contentKey)
				.setVersionKey(versionKey).start();
	}

	@Deprecated
	public NetworkContent getLocal(String locationKey, String contentKey) {
		return getLocal(locationKey, contentKey, H2HConstants.TOMP2P_DEFAULT_KEY);
	}

	@Deprecated
	public NetworkContent getLocal(String locationKey, String contentKey, Number160 versionKey) {
		logger.debug(String.format("local get key = '%s' content key = '%s' version key = '%s'", locationKey,
				contentKey, versionKey));
		Number640 key = new Number640(Number160.createHash(locationKey), H2HConstants.TOMP2P_DEFAULT_KEY,
				Number160.createHash(contentKey), versionKey);
		Data data = getPeer().getPeerBean().storage().get(key);
		if (data != null) {
			try {
				return (NetworkContent) data.object();
			} catch (ClassNotFoundException | IOException e) {
				logger.error(String.format("local get failed exception = '%s'", e.getMessage()));
			}
		} else {
			logger.warn("futureDHT.getData() is null");
		}
		return null;
	}

	public void remove(String locationKey, String contentKey, KeyPair protectionKey, IRemoveListener listener) {
		Number160 lKey = Number160.createHash(locationKey);
		Number160 dKey = H2HConstants.TOMP2P_DEFAULT_KEY;
		Number160 cKey = Number160.createHash(contentKey);
		FutureRemove futureRemove = remove(lKey, dKey, cKey, protectionKey);
		futureRemove.addListener(new FutureRemoveListener(lKey, dKey, cKey, protectionKey, listener, this));
	}

	public void remove(String locationKey, String contentKey, Number160 versionKey, KeyPair protectionKey,
			IRemoveListener listener) {
		Number160 lKey = Number160.createHash(locationKey);
		Number160 dKey = H2HConstants.TOMP2P_DEFAULT_KEY;
		Number160 cKey = Number160.createHash(contentKey);
		FutureRemove futureRemove = remove(lKey, dKey, cKey, versionKey, protectionKey);
		futureRemove.addListener(new FutureRemoveListener(lKey, dKey, cKey, versionKey, protectionKey,
				listener, this));
	}

	public void removeUserProfileTask(String userId, Number160 contentKey, KeyPair protectionKey,
			IRemoveListener listener) {
		Number160 lKey = Number160.createHash(userId);
		Number160 dKey = Number160.createHash(H2HConstants.USER_PROFILE_TASK_DOMAIN);
		FutureRemove futureRemove = remove(lKey, dKey, contentKey, protectionKey);
		futureRemove.addListener(new FutureRemoveListener(lKey, dKey, contentKey, protectionKey, listener,
				this));
	}

	public FutureRemove remove(Number160 locationKey, Number160 domainKey, Number160 contentKey,
			KeyPair protectionKey) {
		logger.debug(String.format("remove location key = '%s' domain key = '%s' content key = '%s'",
				locationKey, domainKey, contentKey));
		return getPeer().remove(locationKey)
				.from(new Number640(locationKey, domainKey, contentKey, Number160.ZERO))
				.to(new Number640(locationKey, domainKey, contentKey, Number160.MAX_VALUE))
				.keyPair(protectionKey).start();
	}

	public FutureRemove remove(Number160 locationKey, Number160 domainKey, Number160 contentKey,
			Number160 versionKey, KeyPair protectionKey) {
		logger.debug(String.format(
				"remove location key = '%s' domain key = '%s' content key = '%s' version key = '%s'",
				locationKey, domainKey, contentKey, versionKey));
		return getPeer().remove(locationKey).setDomainKey(domainKey).contentKey(contentKey)
				.setVersionKey(versionKey).keyPair(protectionKey).start();
	}

	public DigestBuilder getDigest(Number160 locationKey) {
		return getPeer().digest(locationKey);
	}

}
