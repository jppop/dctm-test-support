package org.pockito.dctm.test;

import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;

import com.documentum.com.DfClientX;
import com.documentum.com.IDfClientX;
import com.documentum.fc.client.DfDborEntry;
import com.documentum.fc.client.DfDborNotFoundException;
import com.documentum.fc.client.DfServiceCriticalException;
import com.documentum.fc.client.DfServiceException;
import com.documentum.fc.client.DfServiceNotFoundException;
import com.documentum.fc.client.IDfClient;
import com.documentum.fc.client.IDfDbor;
import com.documentum.fc.client.IDfDborEntry;
import com.documentum.fc.client.IDfPersistentObject;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.common.DfException;
import com.documentum.fc.common.IDfId;
import com.documentum.fc.common.IDfList;
import com.documentum.operations.IDfDeleteOperation;
import com.documentum.operations.IDfOperationError;

public abstract class RepositoryRequiredTest {

	private static ArrayList<IDfId> objectsToDelete = null;

	@Before
	public void ignoreTest() {
		boolean skip = Boolean.valueOf(System.getProperty("skipDctmTest",
				"false"));
		org.junit.Assume.assumeTrue(!skip);
	}

	protected void addToDeleteList(IDfId objectId) {
		if (objectsToDelete == null) {
			objectsToDelete = new ArrayList<IDfId>();
		}
		objectsToDelete.add(objectId);
	}

	@After
	public void cleanUp() throws DfException {
		if (this.dfSession != null && dfSession.isConnected()) {
			try {
				dfSession.getSessionManager().release(dfSession);
			} catch (Exception ignore) {
			}
		}
		dfSession = null;
		if (objectsToDelete != null) {
			IDfSession session = Repository.getInstance().getPrivilegedSession();
			try {
				IDfDeleteOperation operation = new DfClientX().getDeleteOperation();
				operation.setDeepFolders(true);
				operation.setSession(session);
				operation.setVersionDeletionPolicy(IDfDeleteOperation.ALL_VERSIONS);
				for (IDfId objId : objectsToDelete) {
					try {
						IDfPersistentObject dmsObj = session.getObject(objId);
						operation.add(dmsObj);
					} catch (DfException ignore) {
					}
				}
				if (!operation.execute()) {
					IDfList errors = operation.getErrors();
					StringBuilder builder = new StringBuilder();
					for (int i = 0; i < errors.getCount(); i++) {
						IDfOperationError error = (IDfOperationError) errors.get(i);
						builder.append(error.getException()).append("\n");
					}
					System.err.println("Error catched when cleaning up repository after tests. Ignored.");
					System.err.println(builder.toString());
				}
				
			} catch (Exception e) {
				System.err.println("failed to delete test objects: " + e.getMessage());
			} finally {
				Repository.getInstance().releaseSession(session);
				objectsToDelete = null;
			}
		}
	}

	public Repository getRepository() {
		return Repository.getInstance();
	}
	
	protected IDfSession dfSession = null;
	
	public IDfSession getSession() throws DfException {
		if (dfSession == null || !dfSession.isConnected()) {
			dfSession = Repository.getInstance().getPrivilegedSession();
		}
		return dfSession;
	}

	public IDfSession getSession(String repository, String username, String password) throws DfException {
		IDfSession session = Repository.getInstance().getManagedSession(repository, username, password);
		return session;
	}
	
	public void releaseSession(IDfSession session) {
		Repository.getInstance().releaseSession(session);
	}

	@Deprecated
	public static boolean registerSbo(String interfaceName, String implClassname,
			String version) throws DfException {
		boolean registered;
		registered = mapBusinessObjectToClassName(interfaceName, true,
				implClassname, version);
		return registered;
	}

	@Deprecated
	public static boolean registerTbo(String typeName, String implClassname,
			String version) throws DfException {
		boolean registered;
		registered = mapBusinessObjectToClassName(typeName, false,
				implClassname, version);
		return registered;
	}

	@Deprecated
	public static boolean mapBusinessObjectToClassName(String strDborName,
			boolean bDborService, String strJavaClass, String strVersion)
			throws DfException {

		boolean bRetVal = false;
		boolean bAlreadyExists = false;

		// create Client objects
		IDfClientX clientx = new DfClientX();
		IDfClient client = clientx.getLocalClient();

		// get dbor manager
		IDfDbor dbor = client.getDbor();
		try {
			dbor.lookupService(strDborName);
			bAlreadyExists = true;
		}
		// This catch list eats all exceptions because it expects
		// that any of these conditions should still allow us
		// to continue.
		catch (DfDborNotFoundException e) { // dbor.properties file not found
		} catch (DfServiceCriticalException e) { // service found, but it’s a
													// "type"
		} catch (DfServiceNotFoundException e) { // service not found
		}

		// You should get here... service doesn’t exist if adding.
		if (!bAlreadyExists) {
			try {
				dbor.lookupObject(strDborName);
				bAlreadyExists = true;
			}
			// This catch list eats all exceptions because it expects
			// that any of these conditions should still allow us
			// to continue
			catch (DfDborNotFoundException e) { // dbor.properties file not
												// found
			} catch (DfServiceCriticalException e) { // "type" found, but
														// it’s a "service"
			} catch (DfServiceNotFoundException e) { // type not found
			}
		}
		if (bAlreadyExists) {
			// System.out.println(strDborName + " already mapped to " +
			// strLookup);
		} else {
			// You should expect to get here...
			try {
				// String s;
				// if not already registered...
				IDfDborEntry entry = new DfDborEntry();
				entry.setName(strDborName);
				entry.setServiceBased(bDborService);
				entry.setJavaClass(strJavaClass);
				entry.setVersion(strVersion);
				dbor.register(entry);
				// System.out.println("Successful:");
				dbor = null; // unlock DBOR so lookup() can use it.
				// beginList(strDborName); // displays the entry
				bRetVal = true;
			} catch (DfServiceCriticalException e2) {
				// Get here if MSG_DBOR_NOT_DEFINED
				// or MSG_SERVICE_EXISTS
				System.out.println("Unable to map dbor entry");
				System.out.println(e2.toString());
				e2.printStackTrace();
				bRetVal = false;
			} catch (DfServiceException e2) {
				// Get here if DM_VEL_DBOR_IO_ERROR
				// on the registry.
				System.out.println("Unable to map dbor entry");
				System.out.println(e2.toString());
				e2.printStackTrace();
				bRetVal = false;
			}
		}
		return bRetVal;
	}// end mapBusinessObjectToClassName()

	@Deprecated
	public static boolean unmapBusinessObject(String strDborName)
			throws DfException {
		boolean bRetVal = false;
		boolean bAlreadyExists = false;

		// create Client objects
		IDfClientX clientx = new DfClientX();
		IDfClient client = clientx.getLocalClient();

		// get dbor manager
		IDfDbor dbor = client.getDbor();
		try {
			dbor.lookupService(strDborName);
			bAlreadyExists = true;
		}
		// This catch list eats all exceptions because it expects
		// that any of these conditions should still allow us
		// to continue.
		catch (DfDborNotFoundException e) { // dbor.properties file not found
		} catch (DfServiceCriticalException e) { // service found, but it’s a
													// "type"
		} catch (DfServiceNotFoundException e) { // service not found
		}

		// You should get here... service doesn’t exist if adding.
		if (!bAlreadyExists) {
			try {
				dbor.lookupObject(strDborName);
				bAlreadyExists = true;
			}
			// This catch list eats all exceptions because it expects
			// that any of these conditions should still allow us
			// to continue
			catch (DfDborNotFoundException e) { // dbor.properties file not
												// found
			} catch (DfServiceCriticalException e) { // "type" found, but
														// it’s a "service"
			} catch (DfServiceNotFoundException e) { // type not found
			}
		}
		if (bAlreadyExists) {
			try {
				dbor.unregister(strDborName);
				bRetVal = true;
			} catch (Exception ignore) {
			}
		}
		return bRetVal;
	}
}
