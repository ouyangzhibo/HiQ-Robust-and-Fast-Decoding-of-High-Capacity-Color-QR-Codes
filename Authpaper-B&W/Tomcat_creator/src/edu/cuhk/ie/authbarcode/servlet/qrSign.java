package edu.cuhk.ie.authbarcode.servlet;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItemFactory;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.json.JSONObject;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import edu.cuhk.ie.authbarcode.Auth2DbarcodeDecoder;
import edu.cuhk.ie.authbarcode.QRCodeCreator;
import edu.cuhk.ie.authbarcode.colorQRcodeEncoder;
import edu.cuhk.ie.authbarcode.templateHandler.htmlDeparser;
import edu.cuhk.ie.authbarcode.templateHandler.mdDeparser;
import sun.security.ec.ECPrivateKeyImpl;


/**
 * Servlet implementation class qrSign
 */
@WebServlet("/qrSign")
public class qrSign extends HttpServlet {
	private static final long serialVersionUID = 2L;
	private static final long MAX_FILE_SIZE = 1024 * 10 * 1024; //Max size for each file is 10KB
	private static final long MIN_FILE_SIZE = 128; //Min size for each file should be is 128 bytes
	private static final String scannerSuggestion = "Download AuthPaper Scanner to scan this code.";
	private static final String verifierSuggestion = "Download AuthPaper Scanner to verify this code.";
       
	static {
		Security.addProvider(new BouncyCastleProvider());
	}
    /**
     * @see HttpServlet#HttpServlet()
     */
    public qrSign(){
        super();
        // TODO Auto-generated constructor stub
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException {
		//Get request should not be used, treat it as normal QR code creation request
		new qrCreate().doGet(request, response);			
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException {
		//As the Origin header cannot be read in the request object (It is strange), 
		//we do not implement any same-origin checking here
		if(!ServletFileUpload.isMultipartContent(request)){
			new qrCreate().doGet(request, response);
			return;
		}
		//If it is a multipart/form-data request (contains binary data)
		//TODO: read binary data entries
		String message="", senderName="", messageType="", css="", secretJSON="", qrTemplate="", qrTemplateName="text", customSuggestion="", ecLevel="";
		boolean isIncludeCert=false, isTextFormat=false, isAddSuggestion=true, isLargeAlign=false, isColor=false, isShuffleBit=false;
		Map<String,byte[]> imageMap= new HashMap<String,byte[]>();
		Map<String,String> imageType= new HashMap<String,String>();
		KeyCertContainer container = new KeyCertContainer();
		try{
			ServletFileUpload upload = new ServletFileUpload(new DiskFileItemFactory());
			upload.setFileSizeMax(MAX_FILE_SIZE);
			Map<String,List<FileItem>> fields = upload.parseParameterMap(request);
			boolean isKeyReady=false, isCertReady=false;
			for(Map.Entry<String, List<FileItem>> entry : fields.entrySet()){
				String fieldName=entry.getKey();
				FileItem content=entry.getValue().get(0);
				if(!content.isFormField()){
					//Images are stored in non-form fields
					long fileSize = content.getSize();
					String contentType=content.getContentType();
					if(contentType !=null && ( contentType.startsWith("image/") || contentType.startsWith("application/octet-stream") ) 
							&& fileSize <MAX_FILE_SIZE && fileSize>MIN_FILE_SIZE){
						String fileName=content.getName();
						if(fileName !=null && fileName.endsWith(".webp")) imageType.put(fieldName, "webp");
						else if(fileName !=null && fileName.endsWith(".jpg")) imageType.put(fieldName, "jpg");
						else if(fileName !=null && fileName.endsWith(".jpeg")) imageType.put(fieldName, "jpeg");
						else imageType.put(fieldName, "png");
						imageMap.put(fieldName, content.get());
					}
					continue;
				}
				if("inputString".compareTo(fieldName) ==0){					
					message +=new String(content.get(), "utf8");
				}else if(("inputType").compareTo(fieldName) ==0){
					messageType =content.getString();
				}else if("css".compareTo(fieldName) ==0){
					css =content.getString();
				}else if("qrTemplate".compareTo(fieldName) ==0){
					qrTemplate =new String(content.get(), "utf8");
				}else if("qrTemplateName".compareTo(fieldName)==0){
					qrTemplateName =new String(content.get(), "utf8");
				}else if(senderName.isEmpty() && "senderName".compareTo(fieldName) ==0){
					senderName=content.getString();
				}else if("includeCert".compareTo(fieldName) ==0 && "true".compareToIgnoreCase(content.getString()) ==0){
					isIncludeCert=true;
				}else if("isTextFormat".compareTo(fieldName) ==0 && "true".compareToIgnoreCase(content.getString()) ==0){
					isTextFormat=true;	
				}else if("isAddSuggestion".compareTo(fieldName) ==0 && "false".compareToIgnoreCase(content.getString()) ==0){	
					isAddSuggestion=false;
				}else if("isAddSuggestion".compareTo(fieldName) ==0 && content.getString() !=null){
					//Add custom suggestion message
					String suggestion=content.getString();
					if(!suggestion.isEmpty() && "false".compareToIgnoreCase(suggestion) !=0 
							&& "true".compareToIgnoreCase(suggestion) !=0){
						isAddSuggestion=true;
						customSuggestion=suggestion;
					}
				}else if("ecLevel".compareTo(fieldName) ==0 && content.getString() !=null){
					ecLevel=content.getString();
				}else if("largeAlignment".compareTo(fieldName) ==0 && "true".compareToIgnoreCase(content.getString()) ==0){
					isLargeAlign=true;
				}else if("colorQRcode".compareTo(fieldName) ==0 && "true".compareToIgnoreCase(content.getString()) ==0){
					isColor=true;
				}else if("shuffleBits".compareTo(fieldName) ==0 && "true".compareToIgnoreCase(content.getString()) ==0){
					isShuffleBit=true;
				}else if("secretMsg".compareTo(fieldName) ==0){	
					secretJSON =new String(content.get(), "utf8");
				}else if(!isKeyReady && "pKey".compareTo(fieldName) ==0){
					//Notice that the key is sent in string						
					try{
						PEMParser pemReader = new PEMParser(new StringReader(content.getString()));
						Object tmpObject=pemReader.readObject();
		        		for(int counter=0;tmpObject !=null && !isKeyReady && counter<5;counter++){
		        			if(tmpObject instanceof ASN1ObjectIdentifier) {
		        				//What is the usage of getting identifier of a EC private key?
		        			}else if(tmpObject instanceof PEMKeyPair 
		        					|| tmpObject instanceof PrivateKeyInfo){
		        		    	//The private key file is not encrypted
		        		    	PrivateKeyInfo keyInfo = (tmpObject instanceof PEMKeyPair)? 
		        		    			( (PEMKeyPair) tmpObject ).getPrivateKeyInfo()
		        		    			:((PrivateKeyInfo) tmpObject);
		        		    	if(container.savePrivateKey(keyInfo)) isKeyReady=true;			        		    					        		    	
		        		    }else if(tmpObject instanceof ECPrivateKeyImpl){
		        		    	KeyFactory fact;
		        		    	ECPrivateKeyImpl keyImpl=((ECPrivateKeyImpl) tmpObject);
								try{
									fact = KeyFactory.getInstance("EC", "BC");
									PrivateKey pKey = fact.generatePrivate(
											new PKCS8EncodedKeySpec(keyImpl.getEncoded()));
									if(container.savePrivateKey(pKey,keyImpl.getAlgorithm()))
										isKeyReady=true;
								}catch(NoSuchAlgorithmException | InvalidKeySpecException 
										| NoSuchProviderException e2){ }
		        		    }
		        			tmpObject=pemReader.readObject();
		        		}
		        		pemReader.close();
					}catch(IOException e2){ }
				}else if(!isCertReady && "cert".compareTo(fieldName) ==0){
					//Notice that the cert is sent in string
					try{
						PEMParser pemReader = new PEMParser(new StringReader(content.getString()));
						Object tmpObject=pemReader.readObject();
						for(int counter=0;tmpObject !=null && !isCertReady && counter<5;counter++){
							if(tmpObject instanceof X509CertificateHolder){
								try{
									X509Certificate cccert=new JcaX509CertificateConverter()
											.setProvider("BC")
											.getCertificate((X509CertificateHolder) tmpObject);
									if(container.setCertificate(cccert)) isCertReady=true;										
								}catch(CertificateException e2) { }																		
							}								
		        			tmpObject=pemReader.readObject();
		        		}
		        		pemReader.close();
					}catch(IOException e2){ }
				}
			} //End of looping input fields
		}catch(FileUploadException e){
			response.setContentType("text/html");
			PrintWriter pw = response.getWriter();
			pw.println("Error occur. Details : "+e.getMessage());
			return;
		}	

		//Perform the auth2Dbarcode creation
		try{
			PrivateKey pKey=container.getPrivateKey();
			X509Certificate cert=container.getCertificate();
			if(pKey ==null || cert ==null || message.isEmpty())
				throw new Exception("Private key or certificate is not provided");
			
			Auth2DbarcodeDecoder barcodeObj=container.getBarcodeCreatorObj(isTextFormat);
			barcodeObj.isIncludeCert=isIncludeCert;
			if(!senderName.isEmpty()) barcodeObj.setSenderName(senderName);
			int inputSize=(!qrTemplate.isEmpty())? qrTemplate.getBytes().length : message.getBytes().length;
			if(!css.isEmpty()) inputSize +=css.getBytes().length;
			if(!secretJSON.isEmpty()) inputSize +=secretJSON.getBytes().length;
			for(Map.Entry<String, byte[]> entry : imageMap.entrySet()){
				String imgType=imageType.get(entry.getKey());
				barcodeObj.insertData(entry.getKey(), (imgType==null)? "image/*":"image/"+imgType, entry.getValue());
				inputSize +=entry.getValue().length;
			}
			//If alternative template for the qr code exists, save it instead of message
			if(!qrTemplate.isEmpty()){
				barcodeObj.insertData(qrTemplateName, "text/plain",qrTemplate);
			}else{
				if("MD".compareTo(messageType) ==0 || "HTML".compareTo(messageType)==0){
					//The message is a MD file				
					barcodeObj.insertData( ("MD".compareTo(messageType) ==0)? "index.md" : "index.html"
						, "text/plain",message);
					if(!css.isEmpty()) barcodeObj.insertData("index.css", "text/plain",css);
				}else barcodeObj.insertData("plain text", "text/plain",message);
			}
			if(!secretJSON.isEmpty()) barcodeObj.insertData("secretMsg", "application/json", secretJSON);
			barcodeObj.compressAndSignData();
			BufferedImage qrCodeImage=null;
			//Find size of input data in the qr code
			int codeSize=0,qrSize=0,dimension=0;				
			if(!isColor){
				QRCodeCreator qrCodeEncoder=null;
				if(!isTextFormat){
					byte[] resultByte=barcodeObj.getSignedMessageByte();
					codeSize=resultByte.length;
					qrCodeEncoder=new QRCodeCreator(resultByte, 708);				
				}else{
					String resultString = barcodeObj.getSignedMessageString();
					codeSize=resultString.getBytes().length;
					qrCodeEncoder=new QRCodeCreator(resultString, 708);				
				}			
				
				if(qrCodeEncoder !=null){
					qrCodeEncoder.setECLevel( (ecLevel.isEmpty() || ecLevel.compareTo("L")==0)? ErrorCorrectionLevel.L : 
						(ecLevel.compareTo("M")==0)? ErrorCorrectionLevel.M : 
						(ecLevel.compareTo("Q")==0)? ErrorCorrectionLevel.Q	:
						ErrorCorrectionLevel.H ); 
					qrCodeEncoder.isLargeAlign=isLargeAlign;
					qrCodeEncoder.isShuffle=isShuffleBit;
				    qrCodeImage=qrCodeEncoder.encodeAsBitmap();
				    qrSize=qrCodeEncoder.getNumberOfBit() >>3;
					dimension = qrCodeEncoder.getCodeDimension();
				}
			}else{
				colorQRcodeEncoder qrCodeEncoder=null;
				if(!isTextFormat){
					byte[] resultByte=barcodeObj.getSignedMessageByte();
					codeSize=resultByte.length;
					qrCodeEncoder=new colorQRcodeEncoder(resultByte, false, 708);
				}else{
					String resultString = barcodeObj.getSignedMessageString();
					codeSize=resultString.getBytes().length;
					qrCodeEncoder=new colorQRcodeEncoder(resultString, 708);
				}
				if(qrCodeEncoder !=null){
					qrCodeEncoder.isLargeAlign=isLargeAlign;
					qrCodeEncoder.isShuffle=isShuffleBit;
					qrCodeImage=qrCodeEncoder.createColorBitmap();
					qrSize=qrCodeEncoder.getCodeSize();
					dimension=qrCodeEncoder.getCodeDimension();					
				}
			}
			
			if(qrCodeImage ==null) throw new Exception("Cannot create the signed code");
			//Hardcode, add user custom message
		    //Add a text description below the QR code image
		    if(isAddSuggestion){ 
		    	qrCodeImage=addTextBelowImage(qrCodeImage, 
		    		(customSuggestion !=null && !customSuggestion.isEmpty())? customSuggestion : 
		    		(isTextFormat)? verifierSuggestion:scannerSuggestion);
		    }
		    
		    //Create the formatted document, if alternative template is used, add the message and css back
		    if(!qrTemplate.isEmpty()){
		    	if("MD".compareTo(messageType) ==0 || "HTML".compareTo(messageType)==0){
					//The message is a MD file				
					barcodeObj.insertData( ("MD".compareTo(messageType) ==0)? "index.md" : "index.html"
						, "text/plain",message);
					if(!css.isEmpty()) barcodeObj.insertData("index.css", "text/plain",css);
				}
			}
		    htmlDeparser formatDocument = ("MD".compareTo(messageType) ==0)? 
		    	mdDeparser.tryDeparse(barcodeObj) : htmlDeparser.tryDeparse(barcodeObj);
		    String html = (formatDocument ==null)? message  
		    		: htmlDeparser.parseHTMLfromBarcodeLocally(formatDocument, barcodeObj, qrCodeImage); 
			
		    JSONObject dummyObj = new JSONObject();		    
		    dummyObj.put("inputSize", inputSize);
		    dummyObj.put("codeSize", codeSize);
		    dummyObj.put("qrSize", qrSize);
		    dummyObj.put("html", html);		
		    dummyObj.put("dimension", dimension);
		    ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write( qrCodeImage, "PNG", baos );
			baos.flush();
		    dummyObj.put("authCode", Base64.encodeBase64String(baos.toByteArray()));
		    response.setContentType("application/json");
		    response.setCharacterEncoding("UTF-8");
		    PrintWriter pw = response.getWriter();
			pw.println("while(1);"+dummyObj.toString());
			
    		//response.addHeader("Access-Control-Allow-Origin", "*");
    		//response.setContentType("image/jpeg");
    		//OutputStream out = response.getOutputStream();
    		//ImageIO.write(qrCodeImage, "jpg", out);
    		//out.close();        		
    		return;
		}catch(Exception e2){
			response.setContentType("text/html");
			PrintWriter pw = response.getWriter();
			pw.println(e2.getMessage());
			return;
		}
		
	}
	public String getServletInfo() {
        return "The Authenticated 2D barcode creation servlet.";
    }
		
	private static BufferedImage addTextBelowImage(BufferedImage image, String message){
		if(message ==null || message.isEmpty()) return image;
		//wrap the message to fit in the image width and get the new height
		Graphics2D g2d=image.createGraphics();
		Font textFont=new Font("Calibri", Font.BOLD, 40);
		g2d.setColor(Color.BLACK);
		g2d.setFont(textFont);
		FontMetrics fm = g2d.getFontMetrics();
		String[] wrappedString=wrapStringToArray(fm,message,image.getWidth()-4);
		g2d.dispose();
		if(wrappedString ==null) return image;
		int newHeight=image.getHeight()+ (fm.getHeight()*wrappedString.length);
		
		//create the new image
		BufferedImage result = new BufferedImage(image.getWidth(), newHeight, BufferedImage.TRANSLUCENT);
        g2d = result.createGraphics();
        g2d.setPaint(new Color(255,255,255));
        g2d.fillRect(0,0, result.getWidth(), result.getHeight());
        g2d.drawImage(image, 0, 0, null);
        g2d.setColor(Color.BLACK);
        g2d.setFont(textFont);
        fm = g2d.getFontMetrics();
        //Now print the texts line by line
        int textY=image.getHeight()+fm.getAscent();
        for(int i=0,length=wrappedString.length,tHeight=fm.getHeight();i<length;i++){
        	String text=wrappedString[i];
        	int textWidth = fm.stringWidth(text);
        	g2d.drawString(text, (result.getWidth() - textWidth)>>1, textY);
        	textY +=tHeight;
        }     
        g2d.dispose();
        return result;
	}
	/**
	 * Wrap a long sentence into an array of short strings which can be drawed in the given width.
	 * Here we assumed the input string consists of one line only (i.e. no \n) 
	 * @param fm, gives us information about the width, height, etc. of the current Graphics object's Font.
	 * @param s, the message 
	 * @param width, the max width of a string after wrapping
	 * @return
	 */
	private static String[] wrapStringToArray(FontMetrics fm, String s, int width){
		if(width <1 || fm ==null || s ==null || s.isEmpty()) return null;
		String[] words = s.split(" ");
		List<String> result=new ArrayList<String>();
		int position=0;
		String StringSeg="";
		for(String word : words){
			boolean isNewLine=false;
			if(word.endsWith("\r\n")){
				word.replace("\r\n", "");
				isNewLine=true;
			}
			String nextSeg=word + " ";
			int wordLength=fm.stringWidth(nextSeg);
			position +=wordLength;
			// If text exceeds the width, then move to next line.
			if(position >= width){
				result.add(StringSeg);
				position=wordLength;
				StringSeg=nextSeg;	
			}else StringSeg +=nextSeg;
			//Add new line
			if(isNewLine){
				result.add(StringSeg);
				position=0;
				StringSeg="";
			}
		}
		if(!StringSeg.isEmpty()) result.add(StringSeg);
		return (!result.isEmpty())? result.toArray(new String[0]) : null;
	}
}