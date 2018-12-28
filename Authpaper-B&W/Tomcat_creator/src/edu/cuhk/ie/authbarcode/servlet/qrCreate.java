package edu.cuhk.ie.authbarcode.servlet;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;
import org.json.JSONObject;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import edu.cuhk.ie.authbarcode.QRCodeCreator;
import edu.cuhk.ie.authbarcode.colorQRcodeEncoder;

/**
 * Servlet implementation class qrCreate
 */
@WebServlet("/qrCreate")
public class qrCreate extends HttpServlet {
	private static final long serialVersionUID = 2L;
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public qrCreate() {
        super();
        // TODO Auto-generated constructor stub
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		/*Enumeration<String> headers=request.getHeaderNames();
		String origin =request.getHeader("Origin")+"<br>";
		while(headers.hasMoreElements()){
			String header=headers.nextElement();
			origin +=header+" : "+request.getHeader(header)+"<br>";
		}*/
		//As the Origin header cannot be read in the request object (It is strange), 
		//We do not implement any same-origin checking here 	
		String encodeStr=new String(request.getParameter("inputString").getBytes("utf8"), "utf8");
		String ecLevel = request.getParameter("ecLevel");
		if(ecLevel !=null) ecLevel = new String(ecLevel.getBytes("utf8"), "utf8");			
		boolean isLargeAlign= (request.getParameter("largeAlignment") !=null)? true:false;
		boolean isColor = (request.getParameter("colorQRcode") !=null)? true:false; 
		boolean isShuffleBit = (request.getParameter("shuffleBits") !=null)? true:false;
        if((encodeStr != null) && (encodeStr.length() > 0)){
        	try{
        		JSONObject dummyObj = new JSONObject();
    		    dummyObj.put("inputString", encodeStr);
    		    BufferedImage qrCodeImage=null;
        		if(!isColor){
        			QRCodeCreator qrCodeEncoder = new QRCodeCreator(encodeStr, 708);
            		qrCodeEncoder.setECLevel( (ecLevel ==null || ecLevel.isEmpty() || ecLevel.compareTo("L")==0)? ErrorCorrectionLevel.L : 
    					(ecLevel.compareTo("M")==0)? ErrorCorrectionLevel.M : 
    					(ecLevel.compareTo("Q")==0)? ErrorCorrectionLevel.Q	:
    					ErrorCorrectionLevel.H ); 
            		qrCodeEncoder.isLargeAlign=isLargeAlign;
            		qrCodeEncoder.isShuffle=isShuffleBit;
            		qrCodeImage=qrCodeEncoder.encodeAsBitmap();	        		
            		//response.addHeader("Access-Control-Allow-Origin", "*");        		
            		//response.setContentType("image/jpeg");
            		//OutputStream out = response.getOutputStream();
            		//ImageIO.write(qrCodeImage, "jpg", out);
            		//out.close();        		
            		//return;

            		//TODO: How to send out the html and the qrCodeImage altogether??
        		    dummyObj.put("dimension", qrCodeEncoder.getCodeDimension());
        		}else{        			
        			colorQRcodeEncoder qrCodeEncoder=new colorQRcodeEncoder(encodeStr, true, 708);
        			if(ecLevel !=null && !ecLevel.isEmpty() && ecLevel.length() ==3){
        				com.google.zxing.qrcode.decoder.ErrorCorrectionLevel[] eclevels 
        					= new com.google.zxing.qrcode.decoder.ErrorCorrectionLevel[3];
        				for(int i=0;i<3;i++){
        					char ec=ecLevel.charAt(i);
        					eclevels[i] = (ec =='H')? ErrorCorrectionLevel.H
        									:(ec =='Q')? ErrorCorrectionLevel.Q
        									:(ec =='M')? ErrorCorrectionLevel.M
        									:ErrorCorrectionLevel.L;        					
        				}
        				qrCodeEncoder.eclevels=eclevels;
        			}        			
        			qrCodeEncoder.isLargeAlign=isLargeAlign;    
        			qrCodeEncoder.isShuffle=isShuffleBit;
        			qrCodeImage=qrCodeEncoder.createColorBitmap();
        		    dummyObj.put("dimension", qrCodeEncoder.getCodeDimension());
        		}
        		
    		    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    			ImageIO.write( qrCodeImage, "PNG", baos );
    			baos.flush();
    		    dummyObj.put("authCode", Base64.encodeBase64String(baos.toByteArray()));
    		    dummyObj.put("largeAlignment", isLargeAlign);
    		    response.setContentType("application/json");
    		    response.setCharacterEncoding("UTF-8");
    		    PrintWriter pw = response.getWriter();
    			pw.println("while(1);"+dummyObj.toString());
        		return;       		
        	}catch(Exception e2){ }
        }
		//response.addHeader("Access-Control-Allow-Origin", "*");
		response.setContentType("text/html");
		PrintWriter pw = response.getWriter();
		pw.println("<h1>Invalid Input or Service Unavailable</h1>");	
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		//As the Origin header cannot be read in the request object (It is strange), 
		//we do not implement any same-origin checking here 
		if(ServletFileUpload.isMultipartContent(request)){
			//If it is a multipart/form-data request (contains binary data)
			response.setContentType("text/html");
			PrintWriter pw = response.getWriter();
			pw.println("<h1>Not supported yet</h1>");
			return;
		}else{
			doGet(request,response);
		}
	}
	public String getServletInfo() {
        return "The QR Code creation servlet.";
    }

}
