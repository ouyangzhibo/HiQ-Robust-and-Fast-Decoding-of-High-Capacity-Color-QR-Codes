/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android.history;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;


/**
 * This class handles the database saving the scanned 2D barcode history.
 * Please access the database via HistoryFragment
 */
public class HistoryDbHelper extends SQLiteOpenHelper{
	private static final String TAG=HistoryDbHelper.class.getSimpleName();
	public boolean isEncode=false;
	
	private class TableStructure implements BaseColumns {
        public final String TABLE_NAME;
        public final String _ID = BaseColumns._ID;
        public final String COLUMN_NAME_TIME = "time";
        public final String COLUMN_NAME_TEXT = "text";
        public final String COLUMN_NAME_TYPE = "type";
        public final String COLUMN_NAME_RESULT = "rawresult";
        
        private final String TABLE_NAME_SCAN = "barcode_entries";
        private final String TABLE_NAME_ENCODE = "encode_entries";
        TableStructure(boolean isEncodeTable){
        	TABLE_NAME=(isEncodeTable)? TABLE_NAME_ENCODE : TABLE_NAME_SCAN;
        }
    }
	private final TableStructure FeedEntry;
	private final String SQL_CREATE_TABLE, SQL_DROP_TABLE, sortOrder, SQL_COUNT;
	private final String[] projection;
		
	public HistoryDbHelper(Context context, String name, CursorFactory factory,
			int version, boolean isReadingEncodeHistory) {
		super(context, name, factory, version);
		FeedEntry = new TableStructure(isReadingEncodeHistory);
		isEncode = isReadingEncodeHistory;
		SQL_CREATE_TABLE =
			    "CREATE TABLE IF NOT EXISTS " + FeedEntry.TABLE_NAME + " ("
			    + FeedEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
			    + FeedEntry.COLUMN_NAME_TIME + " BIGINT NOT NULL,"
			    + FeedEntry.COLUMN_NAME_TEXT + " VARCHAR(128),"
			    + FeedEntry.COLUMN_NAME_TYPE + " VARCHAR(128) NOT NULL,"
			    + FeedEntry.COLUMN_NAME_RESULT + " VARCHAR(255) NOT NULL"
			    +" )";
		SQL_DROP_TABLE =
			    "DROP TABLE IF EXISTS " + FeedEntry.TABLE_NAME;
		sortOrder=FeedEntry._ID + " DESC";
		SQL_COUNT="SELECT COUNT(*) FROM " 
				+FeedEntry.TABLE_NAME+" WHERE "+FeedEntry._ID+" = ?";
		projection = new String[]{
					    FeedEntry._ID,
					    FeedEntry.COLUMN_NAME_TIME,
					    FeedEntry.COLUMN_NAME_TEXT,
					    FeedEntry.COLUMN_NAME_TYPE,
					    FeedEntry.COLUMN_NAME_RESULT
					};
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		// TODO Create table here, notice that this is called only if a new db is initialized
		db.execSQL(SQL_CREATE_TABLE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion){ }
	
	public long insertEntry(long time, String description, String type, String resultPath){
		if(time<1 || description ==null || description.isEmpty() || type ==null 
				|| type.isEmpty() || resultPath ==null || resultPath.isEmpty()) return -1;
		try{
			SQLiteDatabase db=this.getWritableDatabase();
			db.execSQL(SQL_CREATE_TABLE);
			ContentValues values = new ContentValues();
			values.put(FeedEntry.COLUMN_NAME_TIME, time);
			values.put(FeedEntry.COLUMN_NAME_TEXT, description);
			values.put(FeedEntry.COLUMN_NAME_TYPE, type);
			values.put(FeedEntry.COLUMN_NAME_RESULT, resultPath);
			long newRowId = db.insert(FeedEntry.TABLE_NAME,null,values);
			//return (newRowId >-1)? true:false;
			return newRowId;
		}catch(Exception e2){ 
			//Log.d(TAG, "Something wrong on data insertion : "+e2.toString()+e2.getMessage());
		}
		return -1;
	}
	/**
	 * Get the latest entries
	 * @param limit limit of number of entries
	 * @return
	 */
	public HistoryDbEntry[] getLatestEntries(int limit){
		// if limit less than 1, set no limit
		limit = (limit<1)? -1 : (limit >100)? 100 : limit;
		//TODO: return the data,  how to deserialize the rawResult?????
		SQLiteDatabase db=this.getReadableDatabase();
		Cursor c=null;
		try{
			c = db.query(FeedEntry.TABLE_NAME,projection,null,null,null,null,
					sortOrder,(limit > 0)? ""+limit : null);			
		}catch(Exception e2){
			//Something goes wrong, try to reinitialize the db 
			db.execSQL(SQL_CREATE_TABLE);
		}
		if(c==null) return null;
		int count=c.getCount();
		if(count >0){
			HistoryDbEntry[] results = new HistoryDbEntry[count];
			c.moveToFirst();
			for(int i=0;i<count;i++){
				try{
					HistoryDbEntry entry = new HistoryDbEntry(
							c.getInt(c.getColumnIndexOrThrow(FeedEntry._ID)),
							c.getLong(c.getColumnIndexOrThrow(FeedEntry.COLUMN_NAME_TIME)),
							c.getString(c.getColumnIndexOrThrow(FeedEntry.COLUMN_NAME_TEXT)),
							c.getString(c.getColumnIndexOrThrow(FeedEntry.COLUMN_NAME_TYPE)),
							c.getString(c.getColumnIndexOrThrow(FeedEntry.COLUMN_NAME_RESULT))
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
	public HistoryDbEntry[] getEntriesLaterThan(Long lastUpdateTime){
		SQLiteDatabase db=this.getReadableDatabase();
		Cursor c=null;
		try{
			c = db.query(FeedEntry.TABLE_NAME,projection,"time > "+lastUpdateTime,null,null,null,
					FeedEntry._ID + " ASC",null);			
		}catch(Exception e2){
			//Something goes wrong, try to reinitialize the db 
			db.execSQL(SQL_CREATE_TABLE);
		}
		if(c==null) return null;
		int count=c.getCount();
		if(count >0){
			HistoryDbEntry[] results = new HistoryDbEntry[count];
			c.moveToFirst();
			for(int i=0;i<count;i++){
				try{
					HistoryDbEntry entry = new HistoryDbEntry(
							c.getInt(c.getColumnIndexOrThrow(FeedEntry._ID)),
							c.getLong(c.getColumnIndexOrThrow(FeedEntry.COLUMN_NAME_TIME)),
							c.getString(c.getColumnIndexOrThrow(FeedEntry.COLUMN_NAME_TEXT)),
							c.getString(c.getColumnIndexOrThrow(FeedEntry.COLUMN_NAME_TYPE)),
							c.getString(c.getColumnIndexOrThrow(FeedEntry.COLUMN_NAME_RESULT))
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
	public boolean deleteEntry(int id){
		SQLiteDatabase db=this.getWritableDatabase();
		String whereClause = FeedEntry._ID+" = ?";
		String[] whereArgs = { String.valueOf(id) };
		Cursor c=db.rawQuery(SQL_COUNT, whereArgs);
		c.moveToFirst();
		int count=c.getInt(0);
		c.close();
		if(count <1) return true; //It is already deleted.
		count=db.delete(FeedEntry.TABLE_NAME, whereClause, whereArgs);
		return (count>0);
	}

	public int updateEntry(int id, String description) {
		SQLiteDatabase db=this.getWritableDatabase();
		String whereClause = FeedEntry._ID+" = ?";
		String[] whereArgs = { String.valueOf(id) };
		Cursor c=db.rawQuery(SQL_COUNT, whereArgs);
		int count = c.getCount();
		int recUpdated = 0;
		if (count == 1){ //can only edit one record
			c.moveToFirst();
			ContentValues values = new ContentValues();
			values.put(FeedEntry.COLUMN_NAME_TEXT, description); 
			recUpdated = db.update(FeedEntry.TABLE_NAME, values, whereClause, whereArgs);
		}
		c.close();
		return recUpdated;
	}
	
	public HistoryDbEntry findEntry(long id) {
		HistoryDbEntry entry = null;
		SQLiteDatabase db = this.getWritableDatabase();
		String whereClause = FeedEntry._ID+" = ?";
		String[] whereArgs = { String.valueOf(id) };
		Cursor c = db.query(FeedEntry.TABLE_NAME, projection, whereClause, whereArgs, null, null, null);
		int count = c.getCount();
		if (count == 1) {
			c.moveToFirst();
			entry = new HistoryDbEntry(
					c.getInt(c.getColumnIndexOrThrow(FeedEntry._ID)),
					c.getLong(c.getColumnIndexOrThrow(FeedEntry.COLUMN_NAME_TIME)),
					c.getString(c.getColumnIndexOrThrow(FeedEntry.COLUMN_NAME_TEXT)),
					c.getString(c.getColumnIndexOrThrow(FeedEntry.COLUMN_NAME_TYPE)),
					c.getString(c.getColumnIndexOrThrow(FeedEntry.COLUMN_NAME_RESULT))
					);
			c.close();
		}
		return entry;
	}
}


