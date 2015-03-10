package org.pockito.dctm.test;

import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;

import com.documentum.com.DfClientX;
import com.documentum.fc.client.IDfPersistentObject;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.client.IDfSysObject;
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

	public static Repository getRepository() {
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

	public IDfSysObject createObject(IDfSession session, String objectType, String objectName) throws DfException {
		return createObject(session, objectType, objectName, true);
	}
	
	public IDfSysObject createObject(IDfSession session, String objectType, String objectName, boolean addToDeleteQueue) throws DfException {
		IDfSysObject dmsObject = (IDfSysObject) session.newObject(objectType);
		dmsObject.setObjectName(objectName);
		dmsObject.save();
		if (addToDeleteQueue) {
			addToDeleteList(dmsObject.getObjectId());
		}
		return dmsObject;
	}
}
