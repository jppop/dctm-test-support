package org.pockito.dctm.test;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.Properties;

import org.apache.log4j.Logger;

import com.documentum.com.DfClientX;
import com.documentum.com.IDfClientX;
import com.documentum.fc.client.DfClient;
import com.documentum.fc.client.IDfClient;
import com.documentum.fc.client.IDfDocbrokerClient;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.client.IDfSessionManager;
import com.documentum.fc.client.IDfTypedObject;
import com.documentum.fc.common.DfException;
import com.documentum.fc.common.DfId;
import com.documentum.fc.common.DfLoginInfo;
import com.documentum.fc.common.IDfId;
import com.documentum.fc.common.IDfLoginInfo;

public class Repository {

	static final Logger _logger = Logger.getLogger(Repository.class);

	// hide constructor (avoid multiple instantiation).
	private Repository() {
		loadProperties();
	}

	static class InstanceHolder {
		static Repository instance = new Repository();
	}

	public static Repository getInstance() {
		return InstanceHolder.instance;
	}

	public static Repository getNewInstance() {
		return new Repository();
	}

	/**
	 * Gets a new session. The session is not be managed.
	 * 
	 * @param repository
	 * @param username
	 * @param password
	 * @return
	 * @throws DmsException
	 * @throws DfException 
	 */
	public IDfSession getSession(String repository, String username,
			String password) throws DfException {
		IDfLoginInfo li = new DfLoginInfo();
		li.setUser(username);
		li.setPassword(password);
		li.setDomain(null);

		IDfSession dfSession = null;
		IDfClient client = DfClient.getLocalClient();
		dfSession = client.newSession(repository, li);
		return dfSession;
	}
	
	/**
	 * Gets the session manager.
	 * 
	 * @return
	 * @throws DfException
	 */
	public IDfSessionManager getSessionManager() throws DfException {
		if (_sessMgr == null) {
			synchronized (lockSessionMgr) {
				if (_sessMgr == null) {
					_sessMgr = createSessionManager();
				}
			}
		}
		return _sessMgr;
	}

	/**
	 * Gets a session manager.
	 * 
	 * @return
	 * @throws DfException
	 */
	public IDfSessionManager newSessionManager() throws DfException {
		return createSessionManager();
	}

	/**
	 * Resolves an alias for a repository. Aliases are defined in
	 * Repository.properties config file.
	 * 
	 * @param alias
	 * @return - the resolved alias or the alias it self if no alias is found.
	 */
	public String resolveRepositoryAlias(String alias) {
		String value = alias;
		if (_reposAliases.containsKey(alias)) {
			value = (String) _reposAliases.get(alias);
		}
		return value;
	}

	private Object lockSessionMgr = new Object();

	/**
	 * Gets a DFC session connected to <tt>repository</tt> as <tt>username</tt>.
	 * 
	 * @param repository
	 * @param username
	 * @return
	 * @throws Exception
	 */
	public IDfSession getManagedSession(String repository, String username,
			String password) throws DfException {

		IDfClientX clientX = new DfClientX();

		IDfClient localClient = clientX.getLocalClient();
		IDfTypedObject clientConfig = localClient.getClientConfig();
		String docbroker = getDocbroker();
		if (docbroker != null) {
			clientConfig.setString("primary_host", docbroker);
		}
		String port = getPort();
		if (port != null) {
			clientConfig.setString("primary_port", port);
		}

		IDfLoginInfo li = clientX.getLoginInfo();
		li.setUser(username);
		li.setPassword(password);
		li.setDomain(null);

		// WBD-308 - use aliases for repository names
		String realRepo = resolveRepositoryAlias(repository);

		IDfSessionManager sessMgr = getSessionManager();
		IDfSession dfSession = null;

		synchronized (lockSessionMgr) {
			if (sessMgr.hasIdentity(realRepo)) {
				sessMgr.clearIdentity(realRepo);
			}
			sessMgr.setIdentity(realRepo, li);

			dfSession = sessMgr.getSession(realRepo);
		}

		return dfSession;
	}

	public IDfLoginInfo setIdentity(String repository, String username,
			String password) throws DfException {
		IDfClientX clientX = new DfClientX();

		IDfLoginInfo li = clientX.getLoginInfo();
		li.setUser(username);
		li.setPassword(password);
		li.setDomain(null);

		// WBD-308 - use aliases for repository names
		String realRepo = resolveRepositoryAlias(repository);
		
		IDfSessionManager sessMgr = getSessionManager();

		synchronized (lockSessionMgr) {
			if (sessMgr.hasIdentity(realRepo)) {
				sessMgr.clearIdentity(realRepo);
			}
			sessMgr.setIdentity(realRepo, li);
		}

		return li;
	}
	
	/**
	 * Gets a session for the repository using the current identity.
	 * 
	 * @param repository
	 * @return
	 * @throws DfException
	 */
	public IDfSession getSession(String repository) throws DfException {
		return getSessionManager().getSession(repository);
	}

	/**
	 * Gets a DFC session connected to <tt>repository</tt> as <tt>username</tt>
	 * using login ticket.
	 * 
	 * @param repository
	 * @param username
	 * @return
	 * @throws Exception
	 */
	public IDfSession getManagedSession(String repository, String username)
			throws DfException {

		String ticket = getLoginTicketForUser(repository, username);
		return getManagedSession(repository, username, ticket);
	}

	public IDfSession getManagedSessionForOperator(String repository)
			throws DfException {
		return getManagedSession(repository, getOperatorName(),
				getOperatorPassword());
	}

	/**
	 * Creates a DFC session connected to <tt>repository</tt> as
	 * <tt>username</tt>.
	 * 
	 * @param repository
	 * @param username
	 * @return
	 * @throws Exception
	 */
	public IDfSession newManagedSession(String repository, String username,
			String password) throws DfException {
		return newManagedSession(getSessionManager(), repository, username,
				password);
	}

	public IDfSession newManagedSession(IDfSessionManager sessMgr,
			String repository, String username, String password)
			throws DfException {

		IDfClientX clientX = new DfClientX();

		IDfLoginInfo li = clientX.getLoginInfo();
		li.setUser(username);
		li.setPassword(password);
		li.setDomain(null);

		// WBD-308 - use aliases for repository names
		String realRepo = resolveRepositoryAlias(repository);

		IDfSession dfSession = null;

		synchronized (lockSessionMgr) {
			if (sessMgr.hasIdentity(realRepo)) {
				sessMgr.clearIdentity(realRepo);
			}
			sessMgr.setIdentity(realRepo, li);

			dfSession = sessMgr.newSession(realRepo);
		}
		return dfSession;
	}

	/**
	 * Creates a DFC session connected to <tt>repository</tt> as
	 * <tt>username</tt> using login ticket.
	 * 
	 * @param repository
	 * @param username
	 * @return
	 * @throws Exception
	 */
	public IDfSession newManagedSession(String repository, String username)
			throws DfException {

		String ticket = getLoginTicketForUser(repository, username);
		return newManagedSession(repository, username, ticket);
	}

	public IDfSession newManagedSessionForOperator(String repository)
			throws DfException {
		return newManagedSession(repository, getOperatorName(),
				getOperatorPassword());
	}

	/**
	 * Releases a session.
	 * 
	 * @param dfSession
	 */
	public void releaseSession(IDfSession dfSession) {
		try {
			if (dfSession != null) {
				IDfSessionManager sMgr = dfSession.getSessionManager();
				// try {
				// sMgr.release(dfSession);
				// } catch (Exception e) {
				// dfSession.disconnect();
				// }
				sMgr.release(dfSession);
			}
		} catch (Exception ignore) {
			ignore.printStackTrace();
		}
	}

	/**
	 * Gets a privileged (super user) session.
	 * 
	 * @param repository
	 * @return
	 * @throws DfException
	 */
	public IDfSession getPrivilegedSession(String repository)
			throws DfException {
		IDfSession dfSession = null;
		try {
			IDfClientX clientX = new DfClientX();

			IDfClient localClient = clientX.getLocalClient();
			IDfTypedObject clientConfig = localClient.getClientConfig();
			String docbroker = getDocbroker();
			if (docbroker != null) {
				clientConfig.setString("primary_host", docbroker);
			}
			String port = getPort();
			if (port != null) {
				clientConfig.setString("primary_port", port);
			}

			IDfLoginInfo li = clientX.getLoginInfo();
			li.setUser(getSuUsername());
			li.setPassword(getSuPassword());
			li.setDomain(null);

			// WBD-308 - use aliases for repository names
			String realRepo = resolveRepositoryAlias(repository);

			dfSession = clientX.getLocalClient().newSession(realRepo, li);
		} catch (DfException e) {
			throw e;
		}
		return dfSession;
	}

	public IDfSession getPrivilegedSession()
			throws DfException {
		return getPrivilegedSession(getDocbase());
	}
	
	/**
	 * Releases the privileged session.
	 * 
	 * @param dfSession
	 */
	public void releasePrivilegedSession(IDfSession dfSession) {
		if (dfSession != null) {
			try {
				dfSession.disconnect();
			} catch (DfException ignore) {
				ignore.printStackTrace();
			}
		}
	}

	/**
	 * Gets a login ticket for an user.
	 * 
	 * @param repository
	 * @param username
	 * @return
	 * @throws DfException
	 */
	public String getLoginTicketForUser(String repository, String username)
			throws DfException {
		String ticket = null;
		// WBD-308 - use aliases for repository names
		String realRepo = resolveRepositoryAlias(repository);
		IDfSession dfPriviligedSession = getPrivilegedSession(realRepo);
		try {
			ticket = dfPriviligedSession.getLoginTicketForUser(username);
		} catch (DfException e) {
			throw e;
		} finally {
			releasePrivilegedSession(dfPriviligedSession);
		}
		return ticket;
	}

	/**
	 * Creates a new session manager instance. The session manager does not have
	 * any identities associated with it.
	 * 
	 * @return
	 * 
	 * @throws DfException
	 */
	private static IDfSessionManager createSessionManager() throws DfException {

		IDfClientX clientX = new DfClientX();
		IDfClient localClient = clientX.getLocalClient();
		IDfTypedObject clientConfig = localClient.getClientConfig();
		String docbroker = getDocbroker();
		if (docbroker != null) {
			clientConfig.setString("primary_host", docbroker);
		}
		String port = getPort();
		if (port != null) {
			clientConfig.setString("primary_port", port);
		}
		IDfSessionManager sessMgr = localClient.newSessionManager();
		return sessMgr;
	}

	public static String getDocbroker() {
		return docbroker;
	}

	public void setDocbroker(String docbroker) {
		if (docbroker == null || docbroker.trim().length() == 0
				|| "_default_".equals(docbroker.trim())) {
			Repository.docbroker = null;
		} else {
			Repository.docbroker = docbroker;
		}
	}

	public static String getPort() {
		return port;
	}

	public void setPort(String port) {
		if (port == null || port.trim().length() == 0
				|| "_default_".equals(port.trim())) {
			Repository.port = null;
		} else {
			Repository.port = port;
		}
	}

	public String getDocbase() {
		return repositoryName;
	}

	public void setDocbase(String docbase) {
		this.repositoryName = docbase;
	}

	public String getSuUsername() {
		return suUsername;
	}

	public void setSuUsername(String suUsername) {
		this.suUsername = suUsername;
	}

	public String getSuPassword() {
		return suPassword;
	}

	public void setSuPassword(String suPassword) {
		this.suPassword = suPassword;
	}

	public String getOperatorName() {
		return operatorName;
	}

	public void setOperatorName(String operatorName) {
		this.operatorName = operatorName;
	}

	public String getOperatorPassword() {
		return operatorPassword;
	}

	public void setOperatorPassword(String operatorPassword) {
		this.operatorPassword = operatorPassword;
	}

	public String getRepositoryName() {
		return repositoryName;
	}

	public void setRepositoryName(String repositoryName) {
		this.repositoryName = repositoryName;
	}

	/**
	 * Gets the repository where an object is stored.
	 * 
	 * @param anObjectId
	 * @return
	 */
	public static String getDocbaseNameFromId(final String anObjectId) {
		String docbaseName = null;
		try {
			final IDfId objectId = new DfId(anObjectId);
			if (objectId.isObjectId()) {
				docbaseName = getDocbaseNameFromId(objectId);
			}
		} catch (DfException ignore) {
			_logger.debug(
					"failed to retrieve the docbase name from the object id",
					ignore);
		}
		return docbaseName;
	}

	/**
	 * Gets the repository where an object is stored.
	 * 
	 * @param objectId
	 * @return
	 * @throws DfException
	 */
	public static String getDocbaseNameFromId(final IDfId objectId)
			throws DfException {
		String retval = null;
		if (objectId != null && objectId.isObjectId()) {
			IDfClientX clientX = new DfClientX();

			IDfClient localClient = clientX.getLocalClient();
			IDfTypedObject clientConfig = localClient.getClientConfig();
			String docbroker = getDocbroker();
			if (docbroker != null) {
				clientConfig.setString("primary_host", docbroker);
			}
			String port = getPort();
			if (port != null) {
				clientConfig.setString("primary_port", port);
			}

			final IDfDocbrokerClient docbrokerClient = clientX
					.getDocbrokerClient();
			retval = docbrokerClient.getDocbaseNameFromId(objectId);
		}
		return retval;
	}

	/**
	 * Indicates whether this ID is a valid object ID in the repository. Checks
	 * also the type against the type passed as an argument.
	 * 
	 * @param anObjectId
	 * @param repository
	 * @param type
	 * @return
	 */
	public static boolean isObjectId(final String anObjectId,
			String repository, int type) {
		Boolean isAnId = null;
		try {
			final IDfId objectId = new DfId(anObjectId);
			if (objectId.isObjectId()) {
				if (repository == null) {
					isAnId = Boolean.TRUE;
				} else {
					String docbaseName = getDocbaseNameFromId(objectId);
					isAnId = Boolean.valueOf(repository.equals(docbaseName));
				}
				if (type == -1) {
					if (isAnId == null) {
						isAnId = Boolean.TRUE;
					}
				} else {
					int actualTypePart = objectId.getTypePart();
					isAnId = Boolean.valueOf(type == actualTypePart);
				}
			} else {
				isAnId = Boolean.FALSE;
			}
		} catch (DfException ignore) {
			_logger.debug(
					"failed to retrieve the docbase name from the object id",
					ignore);
		}
		return isAnId.booleanValue();
	}

	private Hashtable<String, String> _reposAliases = new Hashtable<String, String>();

	private void loadProperties() {

		Properties prop = new Properties();
		try {
			String configFilename = System.getProperty("Repository.config");
			InputStream is;
			if (configFilename == null) {
				is = Repository.class
						.getResourceAsStream("Repository.properties");
				if (is == null) {
					is = Repository.class
							.getResourceAsStream("/Repository.properties");
				}
			} else {
				is = new FileInputStream(configFilename);
			}
			if (is != null) {
				prop.load(is);
				setDocbase(prop.getProperty("repository.name"));
				setDocbroker(prop.getProperty("repository.docbroker.host"));
				setPort(prop.getProperty("repository.docbroker.port"));
				setSuUsername(prop.getProperty("repository.su.username"));
				setSuPassword(prop.getProperty("repository.su.password"));
				setOperatorName(prop
						.getProperty("repository.operator.username"));
				setOperatorPassword(prop
						.getProperty("repository.operator.password"));
				// load repository aliases
				try {
					int aliasCount = Integer.parseInt(prop.getProperty(
							"repository.alias.count", "0"));
					for (int aliasIndex = 0; aliasIndex < aliasCount; aliasIndex++) {
						String indexKey = Integer.toString(aliasIndex);
						String name = prop.getProperty("repository.alias.name."
								+ indexKey);
						String value = prop
								.getProperty("repository.alias.value."
										+ indexKey);
						if (name != null && value != null) {
							_reposAliases.put(name, value);
						}
					}
				} catch (NumberFormatException ignore) {
					_logger.debug("invalid aliases count", ignore);
				}
			}
		} catch (IOException ignore) {
			_logger.debug("failed to load default config values", ignore);
		}

	}

	private static String docbroker;
	private static String port;
	// private static String docbase;
	private String suUsername;
	private String suPassword;
	private String operatorName;
	private String operatorPassword;
	private IDfSessionManager _sessMgr = null;
	private String repositoryName;

}
