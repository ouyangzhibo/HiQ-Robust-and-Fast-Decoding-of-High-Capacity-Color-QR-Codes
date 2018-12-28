/**
 * 
 * Copyright (C) 2014 Solon in MobiTeC, CUHK
 *
 */
package edu.cuhk.ie.authbarcodescanner.certificate;

import java.text.SimpleDateFormat;
import java.util.Date;

public class CertificateDbEntry {
	private final int id;
	private final String alias;
	private final String certType;
	private final long date_expire;
	private final long date_issued;
	
	public CertificateDbEntry(int id, String alias, String certType,
			long date_expire, long date_issued) {
		this.id = id;
		this.alias = alias;
		this.certType = certType;
		this.date_expire = date_expire;
		this.date_issued = date_issued;
	}
	
	public int getID(){ return id; }
	public String getAlias(){ return alias; }
	public String getCertType(){ return certType; }
	public long getExpire() { return date_expire; }
	public long getIssued() { return date_issued; }
	public String getDateExpire(){
		return new SimpleDateFormat("dd-MM-yyyy").format(new Date(date_expire));
	}
	public String getDateIssued(){
		return new SimpleDateFormat("dd-MM-yyyy").format(new Date(date_issued));
	}	
}
