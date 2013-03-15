package com.zhuri.ssl;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class EasyX509TrustManager implements X509TrustManager {
	private X509TrustManager standardTrustManager = null;

	public EasyX509TrustManager(KeyStore keystore) {
		super();
		TrustManagerFactory factory = null;
		
		try {
			factory = TrustManagerFactory.getInstance("X509");
			factory.init(keystore);
			TrustManager[] trustmanagers = factory.getTrustManagers();
			if (trustmanagers.length > 0)
				this.standardTrustManager = (X509TrustManager) trustmanagers[0];
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (KeyStoreException e) {
			e.printStackTrace();
		}
	}

	public void checkClientTrusted(X509Certificate[] certificates,
	                               String authType) throws CertificateException {
		this.standardTrustManager.checkClientTrusted(certificates, authType);
	}

	public void checkServerTrusted(X509Certificate[] certificates,
	                               String authType) throws CertificateException {
		if ((certificates != null) && (certificates.length == 1)) {
			X509Certificate certificate = certificates[0];
			try {
				certificate.checkValidity();
			} catch (CertificateException e) {
			}
		} else {
			this.standardTrustManager
			.checkServerTrusted(certificates, authType);
		}
		return;
	}

	public X509Certificate[] getAcceptedIssuers() {
		return this.standardTrustManager.getAcceptedIssuers();
	}
}

