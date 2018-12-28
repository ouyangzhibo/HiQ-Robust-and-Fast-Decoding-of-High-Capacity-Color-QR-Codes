/**
 * 
 * Copyright (C) 2014 Solon in MobiTeC, CUHK
 *
 */
package edu.cuhk.ie.authbarcodescanner.certificate;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import edu.cuhk.ie.authbarcodescanner.android.Log;

public class CertificateDbHelper extends SQLiteOpenHelper{
	private static final String TAG=CertificateDbHelper.class.getSimpleName();
	public static final String DB_NAME = "cert.db";
	public static final int DB_VERSION = 1;
	
	
	private class TableStructure implements BaseColumns {
        public final String TABLE_NAME = "certificate";
        public final String _ID = BaseColumns._ID;
        public final String COLUMN_ALIAS = "alias"; // keystore alias
        public final String COLUMN_CERT_TYPE = "cert_type"; // system or user cert
        public final String COLUMN_DATE_EXPIRE = "date_expire";
        public final String COLUMN_DATE_ISSUED = "date_issued";
    }
	
	public static final String CERT_SYS = "system";
	public static final String CERT_USER = "user";
	private final Set<String> validCertTypes = new HashSet<String>(
		Arrays.asList(new String[] {CERT_SYS, CERT_USER }));
	
	private final TableStructure FeedEntry;
	private final String SQL_CREATE_TABLE, SQL_DROP_TABLE, sortOrder, SQL_COUNT;
	private final String[] projection;	
	
	
	public CertificateDbHelper(Context context, String name, CursorFactory factory, int version) {
		super(context, name, factory, version);
		FeedEntry = new TableStructure();
		SQL_CREATE_TABLE =
			    "CREATE TABLE IF NOT EXISTS " + FeedEntry.TABLE_NAME + " ("
			    + FeedEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
			    + FeedEntry.COLUMN_ALIAS + " VARCHAR(255) NOT NULL UNIQUE,"
			    + FeedEntry.COLUMN_CERT_TYPE + " VARCHAR(10) NOT NULL,"
			    + FeedEntry.COLUMN_DATE_EXPIRE + " BIGINT NOT NULL,"
			    + FeedEntry.COLUMN_DATE_ISSUED + " BIGINT NOT NULL"
			    +" )";
		SQL_DROP_TABLE =
			    "DROP TABLE IF EXISTS " + FeedEntry.TABLE_NAME;
		SQL_COUNT="SELECT COUNT(*) FROM " + FeedEntry.TABLE_NAME;		
		
		sortOrder=FeedEntry.COLUMN_ALIAS + " ASC";
		projection = new String[]{
			    FeedEntry._ID,
			    FeedEntry.COLUMN_ALIAS,
			    FeedEntry.COLUMN_CERT_TYPE,
			    FeedEntry.COLUMN_DATE_EXPIRE,
			    FeedEntry.COLUMN_DATE_ISSUED
			};		
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(SQL_CREATE_TABLE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// Not implemented
	}
	
	// method to get list of certificates
	public CertificateDbEntry[] getCertList(int limit) {
		// if limit less than 1, set no limit
		limit = (limit<1)? -1 : (limit >100)? 100 : limit;
		SQLiteDatabase db=this.getReadableDatabase();
		Cursor c=null;
		try{
			c = db.query(FeedEntry.TABLE_NAME,projection,null,null,null,null,
					sortOrder,(limit>0)? ""+limit : null);			
		}catch(Exception e2){
			//Something goes wrong, try to reinitialize the db 
			db.execSQL(SQL_CREATE_TABLE);
		}
		if(c==null) return null;
		int count=c.getCount();	
		if(count >0){
			CertificateDbEntry[] results = new CertificateDbEntry[count];
			c.moveToFirst();
			for(int i=0;i<count;i++){
				try{
					CertificateDbEntry entry = new CertificateDbEntry
						(
							c.getInt(c.getColumnIndexOrThrow(FeedEntry._ID)),
							c.getString(c.getColumnIndexOrThrow(FeedEntry.COLUMN_ALIAS)),
							c.getString(c.getColumnIndexOrThrow(FeedEntry.COLUMN_CERT_TYPE)),
							c.getLong(c.getColumnIndexOrThrow(FeedEntry.COLUMN_DATE_EXPIRE)),
							c.getLong(c.getColumnIndexOrThrow(FeedEntry.COLUMN_DATE_ISSUED))
						);	
					results[i]=entry;
				}catch(Exception e2){
					results[i]=null;
				}
				if(!c.moveToNext()) break;
			}
			c.close();
			return results;
		}
		c.close();
		return null;		
	}
	
	// method to insert one certificate
	public long insertCert(String alias, String cert_type, long date_exp, long date_iss){
		if(date_exp<1 || date_iss<1 || alias ==null || alias.isEmpty()) return -1;
		// cert_type can only 'system' or 'user'
		if (!validCertTypes.contains(cert_type)) {
			// if cert type not recognised, set as user
			cert_type = CERT_USER;
		}
		try{
			SQLiteDatabase db=this.getWritableDatabase();
			db.execSQL(SQL_CREATE_TABLE);
			ContentValues values = new ContentValues();
			values.put(FeedEntry.COLUMN_ALIAS, alias);
			values.put(FeedEntry.COLUMN_CERT_TYPE, cert_type);
			values.put(FeedEntry.COLUMN_DATE_EXPIRE, date_exp);
			values.put(FeedEntry.COLUMN_DATE_ISSUED, date_iss);
			long newRowId = db.insertOrThrow(FeedEntry.TABLE_NAME,null,values);
			//return (newRowId >-1)? true:false;
			return newRowId;
		}catch(Exception e2){ 
			Log.d(TAG, "Something wrong on data insertion : "+e2.toString()+e2.getMessage());
			Log.d(TAG, "alias : "+alias);
			Log.d(TAG, "cert_type : "+cert_type);
		}
		return -1;
	}	

	// method to delete one certificate
	public int deleteCert(String certId){
			return deleteCert(certId,CERT_USER);
	}
	
	public int deleteCert(String certId, String certType){
		SQLiteDatabase db=this.getWritableDatabase();
		String whereClause = FeedEntry._ID +" = ? AND " + FeedEntry.COLUMN_CERT_TYPE + " == ?";
		String[] whereArgs = { certId, certType };
		int deleted = db.delete(FeedEntry.TABLE_NAME, whereClause, whereArgs);
		return deleted;	
	}
	
	// method to delete certificate by name
	public int deleteCertByName(String alias) {
		SQLiteDatabase db=this.getWritableDatabase();
		String whereClause = FeedEntry.COLUMN_ALIAS +" = ? AND " + FeedEntry.COLUMN_CERT_TYPE + " == ?";
		String[] whereArgs = { alias, CERT_USER };
		int deleted = db.delete(FeedEntry.TABLE_NAME, whereClause, whereArgs);
		return deleted;			
	}
	
	
	// method to get certificate by issuer name
	public CertificateDbEntry getCertificateByAlias (String alias) {
		String selection =	FeedEntry.COLUMN_ALIAS + " = ? ";
		String[] selectionArgs = { alias };
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.query(FeedEntry.TABLE_NAME,null,selection,selectionArgs, null, null, null);
		if (cursor.getCount() == 1) {
			cursor.moveToFirst();
			CertificateDbEntry oldEntry = new CertificateDbEntry
				(
					cursor.getInt(cursor.getColumnIndex(FeedEntry._ID)),
					cursor.getString(cursor.getColumnIndex(FeedEntry.COLUMN_ALIAS)),
					cursor.getString(cursor.getColumnIndex(FeedEntry.COLUMN_CERT_TYPE)),
					cursor.getLong(cursor.getColumnIndex(FeedEntry.COLUMN_DATE_EXPIRE)),
					cursor.getLong(cursor.getColumnIndex(FeedEntry.COLUMN_DATE_ISSUED))
				);
			return oldEntry;
		}
		else {
			return null;
		}
	}
	
	/**
	 * Check if an alias is already occupied under a certain type of certificate
	 * @param alias
	 * @param cert_type
	 * @return
	 */
	public boolean isCertificateExist(String alias, String cert_type){
		if (!validCertTypes.contains(cert_type)) {
			// if cert type not recognised, set as user
			cert_type = CERT_USER;
		}
		String selection =	FeedEntry.COLUMN_ALIAS + " = ? AND " + FeedEntry.COLUMN_CERT_TYPE + " = ?";
		String[] selectionArgs = { alias, cert_type };
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.query(FeedEntry.TABLE_NAME,
				null, 
				selection, 
				selectionArgs, null, null, null);
		return (cursor.getCount() >= 1)? true : false;

	}	
	
	// method to update certificate db details by alias
	// only allow updates to issued and expired
	public int updateCertDate(String alias, String cert_type, long date_iss, long date_exp) {
		SQLiteDatabase db=this.getWritableDatabase();
		String whereClause = FeedEntry.COLUMN_ALIAS + " = ? AND " + FeedEntry.COLUMN_CERT_TYPE + " = ? ";
		String[] whereArgs = { alias, (cert_type !=null && validCertTypes.contains(cert_type))? cert_type:CERT_USER };
		
		ContentValues values = new ContentValues();
		values.put(FeedEntry.COLUMN_DATE_ISSUED, date_iss);
		values.put(FeedEntry.COLUMN_DATE_EXPIRE, date_exp);
		int updated = db.update(FeedEntry.TABLE_NAME, values, whereClause, whereArgs);

		Cursor c = db.query(FeedEntry.TABLE_NAME, null, null, null, null,null, null);
		while(c.moveToNext()) {
			StringBuilder strBld = new StringBuilder();
			strBld.append(c.getInt(c.getColumnIndexOrThrow(FeedEntry._ID)));
			strBld.append(" | ");
			strBld.append(c.getInt(c.getColumnIndexOrThrow(FeedEntry.COLUMN_ALIAS)));
			strBld.append(" | ");
			strBld.append(c.getString(c.getColumnIndexOrThrow(FeedEntry.COLUMN_CERT_TYPE)));
			strBld.append(" | ");
			strBld.append(c.getLong(c.getColumnIndexOrThrow(FeedEntry.COLUMN_DATE_EXPIRE)));
			strBld.append(" | ");
			strBld.append(c.getLong(c.getColumnIndexOrThrow(FeedEntry.COLUMN_DATE_ISSUED)));
			Log.d(TAG, strBld.toString());
		}
		c.close();
		
		return updated;
	}
	
	// placeholder: for viewing certificate details
	public CertificateDbEntry viewCert(long id) {
		CertificateDbEntry entry = null;
		SQLiteDatabase db = this.getWritableDatabase();
		String whereClause = FeedEntry._ID + " =? ";
		String[] whereArgs = { String.valueOf(id) };
		Cursor c = db.query (
					FeedEntry.TABLE_NAME, 
					projection, 
					whereClause, 
					whereArgs, 
					null, 
					null, 
					null 
				);
		int count = c.getCount();
		if (count == 1) {
			c.moveToFirst();
			entry = new CertificateDbEntry
				(
					c.getInt(c.getColumnIndexOrThrow(FeedEntry._ID)),
					c.getString(c.getColumnIndexOrThrow(FeedEntry.COLUMN_ALIAS)),
					c.getString(c.getColumnIndexOrThrow(FeedEntry.COLUMN_CERT_TYPE)),
					c.getLong(c.getColumnIndexOrThrow(FeedEntry.COLUMN_DATE_EXPIRE)),
					c.getLong(c.getColumnIndexOrThrow(FeedEntry.COLUMN_DATE_ISSUED))
				);
		}
		c.close();
		return entry;
	}
}
