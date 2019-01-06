/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android.history;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.google.zxing.Result;


/**
 * Container class of an entry in the database handled by the historyDbHelper class
 */
public class HistoryDbEntry{
	private final int id;
	private final long time;
	private final String description, type;
	private final Result result;
	private final String resultFilePath;
	
	public HistoryDbEntry(int id, long time, String description, String type, Result result){
		this.id=id;
		this.time=time;
		this.description=description;
		this.type=type;
		this.result=result;
		this.resultFilePath=null;
	}
	public HistoryDbEntry(int id, long time, String description, String type, String resultPath){
		this.id=id;
		this.time=time;
		this.description=description;
		this.type=type;
		this.result=null;
		this.resultFilePath=resultPath;
	}
	
	public int getID(){ return id; }
	public String getDescription(){ return description; }
	public String getType(){ return type; }
	public Result getRawResult(){ return result; }
	public String getResultFilePath(){ return resultFilePath;}
	public long getTime(){ return time;}
	public String getDate(){
		return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(time));
	}	
}