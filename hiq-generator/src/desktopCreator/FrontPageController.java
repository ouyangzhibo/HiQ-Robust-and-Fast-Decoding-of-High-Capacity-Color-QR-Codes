package desktopCreator;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import desktopCreator.Main.Dialog;
import sun.security.ec.ECPrivateKeyImpl;
import edu.cuhk.ie.authbarcode.Auth2DbarcodeDecoder;
import edu.cuhk.ie.authbarcode.AuthBarcodePlainText;
import edu.cuhk.ie.authbarcode.ColorQRcodeEncoder;
import edu.cuhk.ie.authbarcode.QRCodeCreator;
import edu.cuhk.ie.authbarcode.templateHandler.documentTemplateItem;
import edu.cuhk.ie.authbarcode.templateHandler.htmlParser;
import edu.cuhk.ie.authbarcode.templateHandler.mdParser;
import edu.cuhk.ie.authbarcode.templateHandler.plainParser;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.JobSettings;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
/**
 * This controller handles the user actions on the FrontPage.fxml
 * Everything that changes the UI of the FrontPage, as well as every popup related to the FrontPage, should be done here.
 * @author Solon Li
 *
 */
public class FrontPageController {
	private static final String TAG = FrontPageController.class.getSimpleName();
	
	public Main mainActivity=null;
	//private List<TextField> inputFields=new ArrayList<TextField>();
	private List<TextInputControl> inputFields=new ArrayList<TextInputControl>();
	private documentTemplateItem currentTemplate=null;
	private KeyCertContainer keyCertContainer=new KeyCertContainer();
	//Notice that the elements starting with @FXML are initialized with the creation of the FrontPage.fxml
	@FXML private TextField senderName;
	@FXML private MenuButton docTemplateMenu;
	private static String defaultTemplateString="Available Templates";
	private boolean isPlainTextTemplate=true;
	private File lastAccessDirectory=null;
	@FXML private WebView templateWebView;
	@FXML private GridPane inputGrid;
	
	@FXML private TextField privatekeySelectorText;
	@FXML private TextField signatureAlgorithmText;	
	@FXML private TextField certSelectorText;
	@FXML private TextField issuerNameText;
	@FXML private CheckBox isCertIncludingClickbox;
	@FXML private CheckBox isForceCompressionClickBox;
	@FXML private CheckBox isDividingClickbox;
	
	@FXML private ImageView image2Dbarcode, image2Dbarcode2, image2Dbarcode3;
	@FXML private Button save2DbarcodeImage, save2DbarcodeImage2, save2DbarcodeImage3;
	private boolean is2DbarcodeCreated=false;
	private static final int LAYERNUMBER = 4;
	//private String HTMLIn2DBarcode;
	
	//Function starting with the @FXML are functions that may be directly called by interacting with the FrontPage.fxml
	/**
	 * Initialize the FrontPage form
	 */
	@FXML protected void initialize(){
		//Security.addProvider(new BouncyCastleProvider());
		Map<String,documentTemplateItem> docTemplateList=getAvailableTemplateList();
		if(docTemplateList !=null && !docTemplateList.isEmpty()){
			docTemplateMenu.getItems().clear();
			//Load the available forms and update the form part 1
			for(Map.Entry<String, documentTemplateItem> entry: docTemplateList.entrySet()){
				MenuItem menuItem = new MenuItem(entry.getKey());
				menuItem.setId(entry.getKey());
				menuItem.setUserData(entry.getValue());
				menuItem.setOnAction(new EventHandler<ActionEvent>() {
				    @Override 
				    public void handle(ActionEvent e) {
				    	MenuItem item = (e.getSource() !=null && e.getSource() instanceof MenuItem)? 
				    			((MenuItem) e.getSource()):null;
				    	if(item ==null) docTemplateMenu.setText(defaultTemplateString);
				    	else{
				    		usedocTemplate( item.getUserData() );
				    		docTemplateMenu.setText( item.getId());
				    	}
				    }
				});
				docTemplateMenu.getItems().add(menuItem);
			}
		}
	}
	
	/**
	 * Load the available document templates, and its necessary input fields from the internal storage.
	 * TODO: Make it as a process connecting to somewhere else in new thread and handle the result in callback function. 
	 * Given that I do not even know where to get the list of templates, I do not know what to do /.\
	 * @return the mapping of templates
	 */
	private Map<String,documentTemplateItem> getAvailableTemplateList(){
		HashMap<String,documentTemplateItem> templateList = new HashMap<String,documentTemplateItem>();
		try{
			//URL url=Thread.currentThread().getContextClassLoader().getResource("/template");
			URL url=getClass().getResource("template/");
			File folder = new File(url.toURI());
			File[] listOfFiles = folder.listFiles(new FilenameFilter() {
			    public boolean accept(File dir, String name) {
			        return ( name.toLowerCase().endsWith(".html") || name.toLowerCase().endsWith(".md") ); 
			    }
			});
			for (File file : listOfFiles) {
			    if (file.isFile()) {
			    	String fileName=file.getName();
			    	documentTemplateItem docItem = null;
			    	if(fileName.endsWith(".html")) docItem = htmlParser.parseDoc(file);
			    	else if(fileName.endsWith(".md")) docItem = mdParser.parseDoc(file);
			    	if(docItem !=null) templateList.put(fileName, docItem);
			    }
			}
		} catch(Exception e){
			Log("Soemthing goes wrong in reading templates. "+e.toString());
			alert("Cannot read the document templates","Soemthing goes wrong in reading templates. "+e.toString());
		}
		templateList.put(plainParser.ID, plainParser.parseDoc());
		return templateList;
	}
	
	/**
	 * Load HTML content into the webview and also set up the input form according to the user selection in part 1  
	 * @param item
	 */
	private void usedocTemplate(Object item){
		if(item !=null && item instanceof documentTemplateItem){
			is2DbarcodeCreated=false;
			String authCodeImagePath=getClass().getResource("img/authCode.png").toString();
			String picIconPath=getClass().getResource("img/pictureIcons.png").toString();
			image2Dbarcode.setImage(new Image(authCodeImagePath));
			image2Dbarcode2.setImage(new Image(authCodeImagePath));
			image2Dbarcode3.setImage(new Image(authCodeImagePath));
			documentTemplateItem template= (documentTemplateItem) item;
			Document doc = Jsoup.parse(template.getTemplate(), "UTF-8");
			currentTemplate=template;
			
			Elements elements=doc.select("img[id]");
	    	for(Element ele : elements){
	    		if(ele.id().compareTo("authCode") ==0) ele.attr("src", authCodeImagePath);
	    		else ele.attr("src", picIconPath);
	    	}
	    	Element element=doc.select("style").first();
	    	if(element !=null) element.html(element.html().replace("display:none", "opacity:0.4"));
	    	templateWebView.getEngine().loadContent(doc.html());
	    	//templateWebView.autosize();
			
			inputGrid.getChildren().clear();
			inputFields.clear();
			isPlainTextTemplate=true;
			int row=0;
			for(Map.Entry<String, documentTemplateItem.entryType> entry: template.entrySet()){
				HBox inputNode=new HBox();
				String inputLabel="";
				TextArea inputTextArea=null;
				//Create a input row according to the type of input
				switch(entry.getValue()){
					case textArea:
						inputTextArea=new TextArea();
						inputTextArea.setPrefRowCount(3);
					case text:
						final TextInputControl inputText=(inputTextArea ==null)? new TextField():inputTextArea;
						inputLabel=entry.getKey();
						inputText.setPromptText(inputLabel);
						inputText.setOnKeyReleased(new EventHandler<KeyEvent>() {
			                @Override
			                public void handle(final KeyEvent e) {
			                	e.consume();
			                	try{
			                		if(inputText !=null && templateWebView !=null 
			                				&& templateWebView.getEngine().getDocument() !=null){
			                			is2DbarcodeCreated=false;
			                			org.w3c.dom.Element textNode=templateWebView.getEngine().getDocument()
			                										  .getElementById(inputText.getPromptText());
			                			//TODO: text filtering according to the text input, currently we use setTextContent 
			                			//to escape HTML
			                			if(textNode !=null) textNode.setTextContent( convertToUTF8(inputText.getText()) );
			                		}
			                	}catch(Exception ex){ }
			                }
			            });
						inputFields.add(inputText);
						inputNode.getChildren().add(inputText);
						break;
					case PNGImage:
					case JPEGImage:
						isPlainTextTemplate=false;
						inputLabel="Select "+entry.getKey();
						final TextField imagePath=new TextField(inputLabel);
						imagePath.setEditable(false);
						imagePath.setPromptText(entry.getKey());
						Button imageChooser=new Button("Select");
						imageChooser.getStyleClass().add("inputButton");
						imageChooser.setOnAction(new EventHandler<ActionEvent>() {
			                @Override
			                public void handle(final ActionEvent e) {
			                	e.consume();
			                	FileChooser fileChooser = new FileChooser();
			                	fileChooser.getExtensionFilters().addAll(		                            
			                            new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png"),
			                            new FileChooser.ExtensionFilter("All", "*.*")
			                    );
			                	is2DbarcodeCreated=false;
			                	if(lastAccessDirectory !=null) fileChooser.setInitialDirectory(lastAccessDirectory);
			                    File file = fileChooser.showOpenDialog(inputGrid.getScene().getWindow());
			                    if (file != null && imagePath !=null && templateWebView !=null 
			                    		&& templateWebView.getEngine().getDocument() !=null) {
			                    	lastAccessDirectory=file.getParentFile();
			                    	imagePath.setText(htmlParser.parseImagePath(file));
			                    	org.w3c.dom.Element imageNode=templateWebView.getEngine().getDocument()
			                    									.getElementById(imagePath.getPromptText());
			                    	if(imageNode !=null) imageNode.setAttribute("src", htmlParser.parseImagePath(file));
			                    }
			                }
			            });
						inputFields.add(imagePath);
						inputNode.getChildren().add(imagePath);
						inputNode.getChildren().add(imageChooser);
					default:
						break;
				}
				//Putting the input row into the input form  
				if(inputNode!=null) {
					inputNode.setId("Input_"+entry.getKey());
					inputNode.setAlignment(Pos.CENTER);
					Label inputLabelText=new Label(inputLabel);
					inputLabelText.getStyleClass().add("inputLabel");
					inputGrid.addRow(row, inputLabelText, inputNode);
					GridPane.setVgrow(inputLabelText, Priority.SOMETIMES);
					GridPane.setVgrow(inputNode, Priority.SOMETIMES);
				}
				row++;
			}
			if(isPlainTextTemplate==true) {
				final Button createPlainQRcode=new Button("Create QR code without digital signature");
				createPlainQRcode.getStyleClass().add("inputButton");
				createPlainQRcode.setOnAction(new EventHandler<ActionEvent>() {
		        	@Override
		            public void handle(ActionEvent e) {
		        		//Step 1: check entries
		        		if(!checkinputEntries(false)) return;
		        		//Step 2: package the data
		        		String message="";
		        		if(currentTemplate.getID().startsWith("plain")) message=convertToUTF8( inputFields.get(0).getText() );
		        		else{
		        			AuthBarcodePlainText barcodeObj=new AuthBarcodePlainText(null,null);
			        		try{
			        			if(currentTemplate.getID().endsWith(".md")) 
			        				mdParser.parseHTML(currentTemplate, inputFields, barcodeObj);
				    			else if(currentTemplate.getID().endsWith(".html")) 
				    				htmlParser.parseHTML(currentTemplate, inputFields, barcodeObj);
				    			else plainParser.parseHTML(currentTemplate, inputFields, barcodeObj);
			        			message=barcodeObj.getunsignedMessageString();
			        		} catch(Exception e2){
			        			message=convertToUTF8( inputFields.get(0).getText() );
			        		}
		        		}
		        		if(message.isEmpty()) alert("Error","No message is prepared to create 2D barcode ");
		        	
		        		//Step 3: Create a QR code containing the data
		        		try {		
		        			BufferedImage[] qrCodeImage = new BufferedImage[LAYERNUMBER];
		        			qrCodeImage[0]=createColorQRcode(message, false);	
		        			//qrCodeImage = createQRcode(message, false);	
		        			displayQRCodeImage(qrCodeImage, message.getBytes("UTF-8").length);		        			
		        		} catch (Exception e1) {
		        			alert("Error","Cannot create 2D barcode with reason : "+e1.getMessage());
		        			Log("Cannot create 2D barcode with reason : "+e1.getMessage());
		        			return;
		        		}
		            }
		        });
				inputGrid.add(createPlainQRcode, 1, row);
				GridPane.setVgrow(createPlainQRcode, Priority.SOMETIMES);
				isForceCompressionClickBox.setText("Compress the data even if it contains plain\n"
												 + "text only (Only our scanner can read it)");
			} else isForceCompressionClickBox.setText("Reduce robustness to save less data\n"
												 + "in the 2D barcode");
			inputGrid.setGridLinesVisible(true);
		}
	}
	

	/**
	 * Fire when the private key "privatekeySelector" or digital certificate "certSelector" selection button is clicked
	 * @param e
	 */
	@FXML protected void selectCert(ActionEvent e){
		//Log("Start Key or Cert Selection");
		if( e==null || !(e.getSource() instanceof Button) ) return;
		Button source=(Button) e.getSource();
		//This line of code depends on the FXML design
		int selectionMode= ( source.getId().compareTo("privatekeySelector")==0 )? 0
							:( source.getId().compareTo("certSelector")==0 )? 1
							:2;
		e.consume();
    	FileChooser fileChooser = new FileChooser();
    	if(selectionMode==0){
    		fileChooser.getExtensionFilters().addAll(
    				new FileChooser.ExtensionFilter("PKCS12 key file", "*.pkcs12", "*.pfx", "*.p12"),
        			new FileChooser.ExtensionFilter("PEM key file", "*.key"),
        			new FileChooser.ExtensionFilter("PPK key file", "*.ppk"),
                    new FileChooser.ExtensionFilter("Others", "*.*")
            );
    	}else if(selectionMode==1){
    		fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("PEM certificate files", "*.pem", "*.cer", "*.cert", "*.crt"),
                    new FileChooser.ExtensionFilter("PKCS12 certificate files", "*.pkcs12", "*.pfx", "*.p12"),
                    new FileChooser.ExtensionFilter("PKCS #7 certificate files", "*.p7b"),
                    new FileChooser.ExtensionFilter("Others", "*.*")
            );
    	}
    	if(lastAccessDirectory !=null) fileChooser.setInitialDirectory(lastAccessDirectory);
    	final File certFile = fileChooser.showOpenDialog(inputGrid.getScene().getWindow());
        if(certFile ==null) return;
        lastAccessDirectory=certFile.getParentFile();
      //After choosing a private key / digital certificate, we need to find the corresponding key / certificate objects
        Object privateKeyObj=null;
    	List<X509CertificateHolder> certList =new ArrayList<X509CertificateHolder>();
        if(certFile.getName().endsWith("p12") || certFile.getName().endsWith("pfx") || certFile.getName().endsWith("pkcs12")){
        	KeyStore p12=null;
        	String password=null;
        	try {
				p12 = KeyStore.getInstance("pkcs12");
				FileInputStream keyStoreStream=new FileInputStream(certFile);
				try{
					password=PromptForPassword(keyStoreStream, p12, e);
				}catch (Exception e2) {
					p12.load(keyStoreStream,null);
				}
			} catch(IOException e2) { } 
        	catch (KeyStoreException e2) { } 
        	catch (NoSuchAlgorithmException e2) { } 
        	catch (CertificateException e2) { } 
        	catch (NullPointerException e2) { }
        	if(p12 ==null){
        		alert("Cannot read the selected file","Cannot read the selected p12/pfx/pkcs12 files.");
        		return;
        	}
    		try{
    			Enumeration<String> aliases = p12.aliases();
    			while(aliases.hasMoreElements()) {
    				String alias = (String) aliases.nextElement();
    				//Log("From PKCS12, get alias : "+alias);
    				if(p12.isCertificateEntry(alias))
    					certList.add( new X509CertificateHolder( p12.getCertificate(alias).getEncoded() ) );
    				else if(privateKeyObj==null && p12.isKeyEntry(alias)){
    					try{        			
    						privateKeyObj = (password !=null)? p12.getKey(alias, password.toCharArray()) : p12.getKey(alias,null);  
    					}catch(UnrecoverableKeyException e3){
    						try{
    							String keyPW=PromptForPassword(alias, p12, e);
    							if(keyPW !=null) privateKeyObj=p12.getKey(alias, keyPW.toCharArray());
    						}catch(Exception e4){continue;} //Something goes wrong, just pass to next step to handle it
    					}
    					if(privateKeyObj !=null && p12.getCertificateChain(alias) !=null){
							Certificate[] certChain=p12.getCertificateChain(alias);
							for(int i=0;i<certChain.length;i++)
								certList.add( new X509CertificateHolder( certChain[i].getEncoded() ) );
						}
    				}
    			}
    		} catch (Exception e2) { }
        }else{
        	PEMParser pemReader;
    		try {
    			pemReader = new PEMParser(new FileReader(certFile));
    			//The first object of the file may not be the key. So we need to loop though the file to find the key object
        		Object tmpObject=pemReader.readObject();
        		for(int counter=0;tmpObject !=null && counter<5;counter++){
        			if(tmpObject instanceof ASN1ObjectIdentifier) {
        				tmpObject=pemReader.readObject();
        				continue; //What is the usage of getting identifier of a EC private key?
        			}
        			if(tmpObject instanceof PEMEncryptedKeyPair || tmpObject instanceof PEMKeyPair 
        					|| tmpObject instanceof PrivateKeyInfo) 
        				privateKeyObj=tmpObject; 
        			if(tmpObject instanceof X509CertificateHolder)
        				certList.add((X509CertificateHolder) tmpObject);
        			tmpObject=pemReader.readObject();        			
        		}
        		pemReader.close();
    		} catch (Exception e1) { }
        }

		if(selectionMode==0){
			if(privateKeyObj !=null && handlePrivateKeyObject(privateKeyObj,e)){
				privatekeySelectorText.setText(certFile.getPath());
		    	signatureAlgorithmText.setText(keyCertContainer.getSignatureAlgorithm());
		    	//Insert the embed cert list only if we can insert the key
		    	if(!certList.isEmpty() && handleCertificateObject(certList,e)){
					certSelectorText.setText(certFile.getPath());
					issuerNameText.setText(keyCertContainer.getCertificateSubjectName());
				}
			} else{
				privatekeySelectorText.clear();
				signatureAlgorithmText.clear();
			}
		}else if(selectionMode==1){
			if(!certList.isEmpty() && handleCertificateObject(certList,e)){
				certSelectorText.setText(certFile.getPath());
				issuerNameText.setText(keyCertContainer.getCertificateSubjectName());
				if(privateKeyObj !=null && handlePrivateKeyObject(privateKeyObj,e)){
					privatekeySelectorText.setText(certFile.getPath());
			    	signatureAlgorithmText.setText(keyCertContainer.getSignatureAlgorithm());
				}
			} else{
				certSelectorText.clear();
				issuerNameText.clear();
			}
		}
		
		
	}
	
	private boolean handlePrivateKeyObject(final Object privateKey, ActionEvent e){		
		try{
			if(privateKey==null) throw new Exception("Unsupported private key file");
			//Log("Get private key with class : "+privateKey.getClass().getName());
			if (privateKey instanceof PEMEncryptedKeyPair) {
		    	//If the selected private key file is encrypted
				//Create a dialog which ask for a password
				PromptForPassword(privateKey,keyCertContainer,e);
				return true;
		    } else if(privateKey instanceof PEMKeyPair || privateKey instanceof PrivateKeyInfo) {
		    	//The private key file is not encrypted
		    	PrivateKeyInfo keyInfo = (privateKey instanceof PEMKeyPair)? 
		    			( (PEMKeyPair) privateKey ).getPrivateKeyInfo():((PrivateKeyInfo) privateKey);
		    	if(keyCertContainer.savePrivateKey(keyInfo)) return true;
		    	else throw new Exception("Signature algorithm for this private key is not supported.");
		    } else if(privateKey instanceof ECPrivateKeyImpl){
		    	KeyFactory fact = KeyFactory.getInstance("ECDSA", "BC");
		    	PrivateKey pKey = fact.generatePrivate(new PKCS8EncodedKeySpec(((ECPrivateKeyImpl) privateKey).getEncoded()));
		    	if( keyCertContainer.savePrivateKey( pKey, ((ECPrivateKeyImpl) privateKey).getAlgorithm() ) ) return true;
		    	else throw new Exception("Signature algorithm for this private key is not supported.");
		    }
		}catch(Exception e2){
			Log("Cannot read valid private key from selected file : "+e2.getMessage());
			alert("Cannot read the private key","Private key detected, but not supported. \n Details:"+e2.getMessage());
		}
		return false;
	}
	
	private boolean handleCertificateObject(List<X509CertificateHolder> certList, ActionEvent e){
		try{
			if(certList==null || certList.isEmpty()) throw new Exception("No certificate is found");
			Log("Get certificate chain with length : "+certList.size());
    		if( keyCertContainer.saveCertificate( certList ) ) return true;
    		else throw new Exception("Extracted digital certificate is not supported.");
		} catch(Exception e1){
			Log("Cannot read valid certificate from selected file : "+e1.getMessage());
			alert("Cannot read the digital certificate","Digital certificate detected, but not supported. \n Details:"+e1.getMessage());
		}
		return false;
	}
	/**
	 * This function will prompt a thread-blocking dialog which asks for password to decrypt the keyFile and save to targetContainer
	 * or get encrypted key with alias equal to keyFile (String) from targetContainer
	 * @param keyFile, support PEMEncryptedKeyPair, FileInputStream object which represent a PKCS12 certificate file and String only
	 * @param targetContainer, if keyFile is PEMEncryptedKeyPair, it must be desktopCreator.KeyCertContainer. 
	 * If keyFile is FileInputStream or String, it must be java.security.KeyStore
	 * @param ActionEvent e 
	 * @return the password gotten
	 * @throws Exception
	 */
	
	private String PromptForPassword(final Object keyFile, final Object targetContainer, ActionEvent e) throws Exception{
		if(keyFile==null || targetContainer==null || e==null) 
			throw new Exception("The key/file is encrypted. But we cannot create a dialog to ask for password");
		if( ( (keyFile instanceof PEMEncryptedKeyPair) && !(targetContainer instanceof KeyCertContainer) ) 
		||  ( (keyFile instanceof FileInputStream) && !(targetContainer instanceof KeyStore) )
		||  ( (keyFile instanceof String) && !(targetContainer instanceof KeyStore) )
		||  ( !(keyFile instanceof FileInputStream) && !(keyFile instanceof PEMEncryptedKeyPair) && !(keyFile instanceof String) ) ) 
			throw new Exception("The key/file is encrypted, but not supported to decrypt it");
		GridPane grid = new GridPane();
		grid.setAlignment(Pos.CENTER);
		grid.setHgap(10);
		grid.setVgap(10);
		grid.setPadding(new Insets(25, 25, 25, 25));		
		final Label pwLabel = new Label("Please provide password for the private key.");
		grid.add(pwLabel, 0, 0, 2, 1); //column 0, row 0, span 2 columns and 1 row only
		GridPane.setHalignment(pwLabel, HPos.CENTER);
    	final TextField pw = new TextField();
    	grid.add(pw, 0, 1, 2, 1); 
		GridPane.setHalignment(pw, HPos.CENTER);
    	Button pwButton = new Button("OK");
    	grid.add(pwButton, 0, 2, 1, 1); 
		GridPane.setHalignment(pwButton, HPos.CENTER);
		Button cancelButton = new Button("Cancel");
		grid.add(cancelButton, 1, 2, 1, 1); 
		GridPane.setHalignment(pwButton, HPos.CENTER);
		final Dialog dialog = mainActivity.getNewDialog("Reading the private key",new Scene(grid));
		//Very bad practice, use a array to hide the return. This only works if the dialog is thread-blocking.
		final String[] pwResult={""}; 
		//If keyFile is inputStream, we save a copy out for repeat password input
		byte[] pkByte=null;
		if(keyFile instanceof FileInputStream){
			FileInputStream pwStream=((FileInputStream) keyFile);
			ByteArrayOutputStream pwOutStream = new ByteArrayOutputStream();
			byte[] buffer = new byte[256];
			int nRead=-1;
			while ((nRead = pwStream.read(buffer, 0, buffer.length)) != -1)
				pwOutStream.write(buffer, 0, nRead);
			pwOutStream.flush();
			pkByte=pwOutStream.toByteArray();
			pwStream.close();
			pwOutStream.close();
			if(pkByte==null) throw new Exception("Cannot read the key file.");
		}
		final byte[] privateKeyByte=pkByte;
		//Now handle the password input
		pwButton.setOnAction(new EventHandler<ActionEvent>() {
        	@Override
            public void handle(ActionEvent event) {
        		String password=pw.getText();        		
        		if(password ==null || password.isEmpty()) return;
        		event.consume();
        	    //if(keyCertContainer.savePrivateKey( (PEMEncryptedKeyPair) privateKey, password )) {
        		if( (keyFile instanceof PEMEncryptedKeyPair) && (targetContainer instanceof KeyCertContainer) 
        			&& ((KeyCertContainer) targetContainer).savePrivateKey((PEMEncryptedKeyPair) keyFile, password) ){
        			pwResult[0]=password;
        	    	dialog.close();
        	    	return;
        	    }
        		else if(targetContainer instanceof KeyStore){  
        			try{
        				Log(keyFile.getClass().toString());
        				if(keyFile instanceof String)
        					((KeyStore) targetContainer).getKey((String) keyFile, password.toCharArray());
        				if(keyFile instanceof FileInputStream){
        					if(privateKeyByte !=null) {
        						ByteArrayInputStream newStream=new ByteArrayInputStream(privateKeyByte);
        						((KeyStore) targetContainer).load(newStream, password.toCharArray());
        					} else throw new KeyStoreException(); //Cannot read the key file
        				}
        				pwResult[0]=password;
            	    	dialog.close();
            	    	return;
        			}catch (UnrecoverableKeyException e2) {
        				Log("Why it is wrong"+e2.getMessage()+e2.toString()+e2.getCause().toString());
        			}
        			catch (IOException e2) {         				
        				Log("Why it is wrong again"+e2.getMessage()+e2.toString());
        				if(e2.getCause()!=null) Log(e2.getCause().getMessage()+e2.getCause().toString());
        			} //Wrong password, try again
	            	catch (NoSuchAlgorithmException | CertificateException | KeyStoreException e2) { 
	            		pwResult[0]="";
            	    	dialog.close();
            	    	return;
	            	}
        		}
    	    	pwLabel.setText("Incorrect Password. Please input again.");
    	    	pw.clear();
            }
        });
    	cancelButton.setOnAction(new EventHandler<ActionEvent>() {
        	@Override
            public void handle(ActionEvent event) {
        		dialog.close();
            }
        });
		//Note: the code after showDialog() will be executed after the event loop containing dialog.hide() is executed  
		dialog.showDialog();
		if(pwResult[0]==null || pwResult[0].isEmpty()) throw new Exception("Authentication failure.");
		return pwResult[0];
	}
	
	/**
	 * Create the Authenticated 2D barcode and hence the document according to the selected document template
	 * @param e
	 */
	@FXML protected void create2DBarcode(ActionEvent e){
		//Log( (String) templateWebView.getEngine().executeScript("document.documentElement.innerHTML") );
		is2DbarcodeCreated=false;
		boolean isIncludeCert=isCertIncludingClickbox.isSelected();
		if(isIncludeCert)
			alert("Warning","The 2D barcode may not able to store the digital certificate with the input data");
		//Step 1: check entries
		if(!checkinputEntries(true)) return;
		//Step 2: Package the data into a container and sign it
		boolean isPlainText=(isPlainTextTemplate && !isForceCompressionClickBox.isSelected())? 
								true:false;
		Auth2DbarcodeDecoder barcodeObj=keyCertContainer.getBarcodeCreatorObj(isPlainText);
		barcodeObj.isIncludeCert=isIncludeCert;
		if(senderName.getText() !=null && !senderName.getText().isEmpty()) barcodeObj.setSenderName(senderName.getText());
		try{
			if(currentTemplate.getID().endsWith(".md")) mdParser.parseHTML(currentTemplate, inputFields, barcodeObj);
			else if(currentTemplate.getID().endsWith(".html")) htmlParser.parseHTML(currentTemplate, inputFields, barcodeObj);
			else plainParser.parseHTML(currentTemplate, inputFields, barcodeObj);
			barcodeObj.compressAndSignData();
		} catch(Exception e2){
			alert("Error","Cannot create Authenticated 2D barcode with reason : "+e2.getMessage());
			Log("Cannot create Authenticated 2D barcode with reason : "+e2.getMessage());
			return;
		}
		//Step 3: Create a QR code containing the data
		BufferedImage[] qrCodeImage = new BufferedImage[3];	
		int barcodeImageContentSize=0;
		try {		
			if(isPlainText) {
				String resultStr=barcodeObj.getSignedMessageString();
				//qrCodeImage=createQRcode(resultStr, true);
				qrCodeImage[0]=createColorQRcode(resultStr, true);
				barcodeImageContentSize=resultStr.getBytes("UTF-8").length;
			}else{
				byte[] resultByte=barcodeObj.getSignedMessageByte();
				//qrCodeImage=createQRcode(resultByte);
				qrCodeImage[0]=createColorQRcode(resultByte);
				barcodeImageContentSize=resultByte.length;
			}
		} catch (Exception e1) {
			alert("Error","Cannot create Authenticated 2D barcode with reason : "+e1.getMessage());
			Log("Cannot create Authenticated 2D barcode with reason : "+e1.getMessage());
			return;
		}
		
		//Step 4: display the result		
		displayQRCodeImage(qrCodeImage, barcodeImageContentSize);
	}
	
	/**
	 * Check that everything in the input field is inserted, and change them into UTF-8 encoding
	 */
	protected boolean checkinputEntries(boolean isSigning){
		try{
			if(inputFields ==null ||inputFields.isEmpty()) throw new Exception("No template is selected.");
			Iterator<TextInputControl> iterator=inputFields.iterator();
			while(iterator.hasNext()){
				TextInputControl inputField=iterator.next();
				if(inputField.getText().isEmpty()) throw new Exception("Input field : "+inputField.getPromptText()+" is empty.");
				inputField.setText( convertToUTF8(inputField.getText()) );
			}
			if(!isSigning) return true; 
			if(keyCertContainer.getPrivateKey() ==null || keyCertContainer.getSignatureAlgorithm() ==null 
					|| keyCertContainer.getCertificate()==null)
				throw new Exception("No private key / digital certificate is selected.");
			return true;
		} catch(Exception e2){
			alert("Error",e2.getMessage());
		}
		return false;
	}
	
	private BufferedImage[] createQRcode(String resultStr, boolean isSigning) throws Exception{
		BufferedImage[] qrCodeImage = new BufferedImage[LAYERNUMBER];
		QRCodeCreator qrCodeEncoder=null;			
		//And then create a QR code based on the given data
		if(isDividingClickbox.isSelected()){
			//Divide the data into three codes natively			
			int strLength = resultStr.length();
			int padding =strLength %LAYERNUMBER;
			if(padding ==1) resultStr +="\n\n";
			if(padding ==2) resultStr +="\n";
			strLength = resultStr.length();
			//To fulfill the structure append mode, we need to create a parity data
			byte parity=0x0;
			byte[] strByte=resultStr.getBytes("UTF-8");
			for(int i=0, count=strByte.length;i<count;i++){
				parity ^= strByte[i];
			}
			int chunkLength = strLength/LAYERNUMBER;			
			String errStr="";			
			for(int i=0,j=0;i<strLength;i+=chunkLength){				
				if(j>=2) chunkLength = strLength - i;
				qrCodeEncoder = new QRCodeCreator(resultStr.substring(i, chunkLength + i), 708, 0);				
				qrCodeEncoder.setECLevel(ErrorCorrectionLevel.M);
				if(isSigning){
					//Include the seq byte for structure append mode, format : first 4 bits seq number, next 4 bits total # QR codes
				    qrCodeEncoder.setQRQppend((byte) (((0x03 & j)<<4) & 0x02), parity);
				}
				try{
					qrCodeImage[j++]=qrCodeEncoder.encodeAsBitmap();
				}catch(Exception e2){
					errStr+="The "+j+"th QRcode : "+e2.getMessage()+"\n";
					qrCodeImage[j++]=null;
				}
			}
			if(qrCodeImage[0] ==null || qrCodeImage[1] ==null || qrCodeImage[2]==null)
		    	throw new Exception(errStr);
		}else{			
			qrCodeEncoder = new QRCodeCreator(resultStr, 708, 0);			
			qrCodeEncoder.setECLevel(ErrorCorrectionLevel.L);
			qrCodeImage[0]=qrCodeEncoder.encodeAsBitmap();				
		}
		Log("Create 2D barcode with error correction : "+qrCodeEncoder.getECLevel().toString()
				+" , data size : " + qrCodeEncoder.getNumberOfBit() +" bits or modules per QR code"
				+" and user input size : " + resultStr.getBytes("UTF-8").length);
		return qrCodeImage;
	}
	
	private BufferedImage createColorQRcode(String resultStr, boolean isSigning) throws Exception{
		BufferedImage colorQRCodeImage = null;
		QRCodeCreator qrCodeEncoder=null;			
		boolean isDifECL = true; // different error correction level
		//And then create a QR code based on the given data
		if(isDividingClickbox.isSelected()){
			BitMatrix[] bitMatrices = new BitMatrix[LAYERNUMBER];
			//Divide the data into three codes natively
			if (isDifECL) {
				com.google.zxing.qrcode.encoder.QRCode[] qrCodes=null;
				ErrorCorrectionLevel[] eclevels = new ErrorCorrectionLevel[LAYERNUMBER];
				for(int i = 0; i<eclevels.length; i++)
					eclevels[i]=ErrorCorrectionLevel.L;
//				eclevels[0]=ErrorCorrectionLevel.L;
//				eclevels[1]=ErrorCorrectionLevel.L;
//				eclevels[2]=ErrorCorrectionLevel.Q;
				Map<EncodeHintType,Object> hints = new EnumMap<EncodeHintType,Object>(EncodeHintType.class);
				hints.put(EncodeHintType.MARGIN, 0);
				hints.put(EncodeHintType.QR_StructureAppend, new byte[]{0x0, 0x0});
				qrCodes=ColorQRcodeEncoder.divideByteIntoMultiQRs(resultStr,eclevels, hints);				
				alert("Input size:","Size:"+resultStr.getBytes("UTF-8").length+" and version of a QR code is "+qrCodes[0].getVersion().getVersionNumber());
				for(int i=0;i<LAYERNUMBER;i++)
					bitMatrices[i] = QRCodeCreator.renderResult(QRCodeCreator.repaintPatternsStatic(qrCodes[i].getMatrix(), qrCodes[0].getVersion(), i+1, LAYERNUMBER), 708, 708);
				Log(" bits or modules per QR code and user input size : " + resultStr.getBytes("UTF-8").length);
			} else {
				int strLength = resultStr.length();
				int padding =strLength %LAYERNUMBER;
				if(padding ==1) resultStr +="\n\n";
				if(padding ==2) resultStr +="\n";
				strLength = resultStr.length();
				//To fulfill the structure append mode, we need to create a parity data
				byte parity=0x0;
				byte[] strByte=resultStr.getBytes("UTF-8");
				for(int i=0, count=strByte.length;i<count;i++){
					parity ^= strByte[i];
				}
				int chunkLength = strLength/LAYERNUMBER;			
				for(int i=0,j=0;i<strLength;i+=chunkLength){				
					if(j>=2) chunkLength = strLength - i;
					qrCodeEncoder = new QRCodeCreator(resultStr.substring(i, chunkLength + i), 708, j+1);				
					qrCodeEncoder.setECLevel(ErrorCorrectionLevel.M);
					if(isSigning){
						//Include the seq byte for structure append mode, format : first 4 bits seq number, next 4 bits total # QR codes
					    qrCodeEncoder.setQRQppend((byte) (((0x03 & j)<<4) & 0x02), parity);
					}
					bitMatrices[j++] = qrCodeEncoder.getBitMatrix();
				}
				Log("Create 2D barcode with error correction : "+qrCodeEncoder.getECLevel().toString()
						+" , data size : " + qrCodeEncoder.getNumberOfBit() +" bits or modules per QR code"
						+" and user input size : " + resultStr.getBytes("UTF-8").length);
			}
			colorQRCodeImage = QRCodeCreator.encodeAsColorBitmap(bitMatrices);
		}else{			
			qrCodeEncoder = new QRCodeCreator(resultStr, 708, 0);			
			qrCodeEncoder.setECLevel(ErrorCorrectionLevel.L);
			colorQRCodeImage=qrCodeEncoder.encodeAsBitmap();			
			Log("Create 2D barcode with error correction : "+qrCodeEncoder.getECLevel().toString()
					+" , data size : " + qrCodeEncoder.getNumberOfBit() +" bits or modules per QR code"
					+" and user input size : " + resultStr.getBytes("UTF-8").length);
		}
		
		return colorQRCodeImage;
	}
	
	private BufferedImage[] createQRcode(byte[] resultByte) throws Exception{
		BufferedImage[] qrCodeImage = new BufferedImage[LAYERNUMBER];
		QRCodeCreator qrCodeEncoder=null;		
		ErrorCorrectionLevel ecLevel = (isForceCompressionClickBox.isSelected())? 
				ErrorCorrectionLevel.L:ErrorCorrectionLevel.L;
		//Add additional error correction on the whole data, hard code 10% error correction
	/*	int numCodewords = (int) (resultByte.length*1.2); //Round down
	    // First read into an array of ints
	    int[] codewordsInts = new int[numCodewords];
	    for (int i = 0; i < resultByte.length; i++) {
	      codewordsInts[i] = resultByte[i] & 0xFF;
	    }
	    int numECCodewords = numCodewords - resultByte.length;
	    try{
	      ReedSolomonEncoder rsDecoder = new ReedSolomonEncoder(GenericGF.AZTEC_DATA_12);
	      rsDecoder.encode(codewordsInts, numECCodewords);
	      resultByte = new byte[numCodewords];
	      for(int i = 0; i < numCodewords; i++) 
	    	  resultByte[i] = (byte) codewordsInts[i];
	    }catch (IllegalArgumentException e2){ 
	    	Log(e2.toString()+e2.getMessage());
	    }
	    */
		if(isDividingClickbox.isSelected()){
			//Divide the data into three codes natively
			int strLength = resultByte.length;
			int padding =strLength %LAYERNUMBER;
			//To fulfill the structure append mode, we need to create a parity data
			byte parity=0x0;
			for(int i=0;i<strLength;i++){
				parity ^= resultByte[i];
			}
			strLength+=padding;
			//As Arrays.copyOfRange will automatically append byte by 0x00, so we do not need to do it ourselves
			int chunkLength = strLength/LAYERNUMBER;
			String errStr="";
			for(int i=0,j=0;i<strLength;i+=chunkLength){
				if(j>=2) chunkLength = strLength - i;
				qrCodeEncoder = new QRCodeCreator(
						Arrays.copyOfRange(resultByte, i, chunkLength + i), 708, 0);
			    qrCodeEncoder.setECLevel(ecLevel);
			    //Include the seq byte for structure append mode, format : first 4 bits seq number, next 4 bits total # QR codes
			    qrCodeEncoder.setQRQppend((byte) (((0x03 & j)<<4) & 0x02), parity);
			    try {
			    	qrCodeImage[j++]=qrCodeEncoder.encodeAsBitmap();
				}catch(Exception e2){
					errStr+="The "+j+"th QRcode : "+e2.getMessage()+"\n";
					qrCodeImage[j++]=null;
				}
			}
			if(qrCodeImage[0] ==null || qrCodeImage[1] ==null || qrCodeImage[2]==null)
		    	throw new Exception(errStr);
			
		}else{
			qrCodeEncoder = new QRCodeCreator(resultByte, 708, 0);
		    qrCodeEncoder.setECLevel(ecLevel);
		    qrCodeImage[0]=qrCodeEncoder.encodeAsBitmap();					
		}
		Log("Create 2D barcode with error correction : "+qrCodeEncoder.getECLevel().toString()
				+" , data size : " + qrCodeEncoder.getNumberOfBit() +" bits or modules per QR code"
				+" and user input size : " + resultByte.length);
		return qrCodeImage; 		
	}
	
	private BufferedImage createColorQRcode(byte[] resultByte) throws Exception{
		BufferedImage colorQRCodeImage = null;
		QRCodeCreator qrCodeEncoder=null;		
		ErrorCorrectionLevel ecLevel = (isForceCompressionClickBox.isSelected())? 
				ErrorCorrectionLevel.L:ErrorCorrectionLevel.M;
//		ErrorCorrectionLevel ecLevel = ErrorCorrectionLevel.M;
		if(isDividingClickbox.isSelected()){
			BitMatrix[] bitMatrices = new BitMatrix[LAYERNUMBER];
			//Divide the data into three codes natively
			int strLength = resultByte.length;
			int padding =strLength %LAYERNUMBER;
			//To fulfill the structure append mode, we need to create a parity data
			byte parity=0x0;
			for(int i=0;i<strLength;i++){
				parity ^= resultByte[i];
			}
			strLength+=padding;
			//As Arrays.copyOfRange will automatically append byte by 0x00, so we do not need to do it ourselves
			int chunkLength = strLength/LAYERNUMBER;
			for(int i=0,j=0;i<strLength;i+=chunkLength){
				if(j>=2) chunkLength = strLength - i;
				qrCodeEncoder = new QRCodeCreator(
						Arrays.copyOfRange(resultByte, i, chunkLength + i), 708, j+1);
					qrCodeEncoder.setECLevel(ecLevel);
			    //Include the seq byte for structure append mode, format : first 4 bits seq number, next 4 bits total # QR codes
			    qrCodeEncoder.setQRQppend((byte) (((0x03 & j)<<4) | 0x03), parity);
			    bitMatrices[j++] = qrCodeEncoder.getBitMatrix();
			}
			colorQRCodeImage = QRCodeCreator.encodeAsColorBitmap(bitMatrices[0], bitMatrices[1], bitMatrices[2]);
		}else{
			qrCodeEncoder = new QRCodeCreator(resultByte, 708, 0);
		    qrCodeEncoder.setECLevel(ecLevel);
		    colorQRCodeImage=qrCodeEncoder.encodeAsBitmap();					
		}
		Log("Create 2D barcode with error correction : "+qrCodeEncoder.getECLevel().toString()
				+" , data size : " + qrCodeEncoder.getNumberOfBit() +" bits or modules per QR code"
				+" and user input size : " + resultByte.length);
		
		return colorQRCodeImage; 		
	}
	
	
	protected void displayQRCodeImage(BufferedImage[] qrCodeImage, int barcodeImageContentSize){		
		if(qrCodeImage !=null && qrCodeImage.length ==LAYERNUMBER){
			if (LAYERNUMBER >= 3) {
				if(qrCodeImage[0] !=null && qrCodeImage[1] ==null && qrCodeImage[2] ==null
						&& displayQRCodeImage(image2Dbarcode, qrCodeImage[0], "tmpauthCode", true)){
					alert("Complete","2D barcode is created. "
							+ "It stores "+barcodeImageContentSize+" bytes of data (excluding error corrections).");
					is2DbarcodeCreated=true;				
				}else if(displayQRCodeImage(image2Dbarcode, qrCodeImage[0], "tmpauthCode", false)
				&& displayQRCodeImage(image2Dbarcode2, qrCodeImage[1], "tmpauthCode2", false)
				&& displayQRCodeImage(image2Dbarcode3, qrCodeImage[2], "tmpauthCode3", false)){
					alert("Complete","Three 2D barcodes are created. The QR codes store "
							+barcodeImageContentSize+" bytes of data (excluding error corrections) in total.");
					is2DbarcodeCreated=true;
				}
			} else if (LAYERNUMBER == 2){
				if(qrCodeImage[0] !=null && qrCodeImage[1] ==null && displayQRCodeImage(image2Dbarcode, qrCodeImage[0], "tmpauthCode", true)){
					alert("Complete","2D barcode is created. "
							+ "It stores "+barcodeImageContentSize+" bytes of data (excluding error corrections).");
					is2DbarcodeCreated=true;				
				}else if(displayQRCodeImage(image2Dbarcode, qrCodeImage[0], "tmpauthCode", false)
				&& displayQRCodeImage(image2Dbarcode2, qrCodeImage[1], "tmpauthCode2", false)){
					alert("Complete","Three 2D barcodes are created. The QR codes store "
							+barcodeImageContentSize+" bytes of data (excluding error corrections) in total.");
					is2DbarcodeCreated=true;
				}
			}
		}
	}
	
	protected boolean displayQRCodeImage(ImageView imageView, BufferedImage qrCodeImage, String tempFileName, 
			boolean isShowOnHTML){
		if(qrCodeImage !=null && imageView !=null && tempFileName !=null){
			imageView.setImage(SwingFXUtils.toFXImage(qrCodeImage, null));
			//if(entry.getKey().compareTo("authCode") ==0) continue;
		    try {
		    	URL url=getClass().getResource("temp/");
				File tempDir = new File(url.toURI());
				File tempFile = File.createTempFile((tempFileName.isEmpty())? "tmpauthCode":tempFileName, ".png", tempDir);
				tempFile.deleteOnExit();
				ImageIO.write(qrCodeImage, "png",tempFile);
				if(isShowOnHTML) htmlParser.set2DbarcodeImage(templateWebView.getEngine().getDocument(), tempFile);
				else{
					htmlParser.set2DbarcodeImage(templateWebView.getEngine().getDocument(),
							getClass().getResource("img/authCode.png").toString()); 
				}					
				return true;
			} catch (IOException | URISyntaxException e1) {
				alert("Error","Cannot put the barcode image into the document : "+e1.getMessage());
				Log("Cannot put the barcode image into the document : "+e1.getMessage());				
			}
		}
		return false;
	}
	
	/**
	 * Fired when user click a button to save the created 2D barcode image in image2dbarcode ImageView
	 * @param e
	 */
	@FXML protected void saveCreated2DbarcodeImage(ActionEvent e){
		if(!is2DbarcodeCreated){
			alert("Error","The 2D barcode is not created / does not contain the current input");
			return;
		}
		FileChooser fileChooser = new FileChooser();
    	fileChooser.getExtensionFilters().addAll(		                            
                new FileChooser.ExtensionFilter("PNG", "*.png")
        );
    	fileChooser.setInitialFileName("auth2Dbarcode.png");
    	File file=fileChooser.showSaveDialog(inputGrid.getScene().getWindow());
    	try {
    		if(file ==null) throw new IOException("no file is selected.");
    		Object source=e.getSource();
    		ImageView selected2DbarcodeImage =(save2DbarcodeImage3.equals(source))? image2Dbarcode3 
    											: (save2DbarcodeImage2.equals(source))? image2Dbarcode2
    											:image2Dbarcode;
    		if(selected2DbarcodeImage ==null || selected2DbarcodeImage.getImage() ==null) 
    			throw new IOException("no image to save.");
    		//Save the image to the selected file
    		BufferedImage qrCodeImage=SwingFXUtils.fromFXImage(selected2DbarcodeImage.getImage(), null);
			ImageIO.write(qrCodeImage, "png", file);
			alert("Saving Completed","The image is saved.");
		} catch (IOException e1) {
			alert("Error","Cannot save image into the selected location : "+e1.getMessage());
		}        
	}
	private JobSettings lastPrinterSetting=null; 
	private Printer lastPrinter=null;
	/**
	 * Fired when user click a button to save the created document in the WebView
	 * @param e
	 */
	@FXML protected void saveCreatedDocument(ActionEvent e){
		if(!is2DbarcodeCreated || templateWebView.getEngine().getDocument()==null){
			alert("Error","The 2D barcode is not created / does not contain the current input");
			return;
		}
		PrinterJob job=null;
		boolean isOldPrinter=false;
		try{
			if(lastPrinter !=null){
				job=PrinterJob.createPrinterJob(lastPrinter);
				if(job !=null) isOldPrinter=true;
			}
			if(job ==null) job=PrinterJob.createPrinterJob();
		} catch(SecurityException e2){ }
		if(job ==null) {
			alert("Error","Cannot connect to the printer service.");
			return;
		}
		JobSettings currentSettings=job.getJobSettings();
		String currentTemplateName=(currentTemplate !=null)? currentTemplate.getID():"";
		currentSettings.setJobName( (!currentTemplateName.isEmpty())? 
				removeSuffix(currentTemplateName)+".pdf":"authenticated_paper.pdf");
		if(isOldPrinter && lastPrinterSetting !=null){
			currentSettings.setCollation(lastPrinterSetting.getCollation());
			currentSettings.setCopies(lastPrinterSetting.getCopies());
			currentSettings.setPageLayout(lastPrinterSetting.getPageLayout());
			currentSettings.setPaperSource(lastPrinterSetting.getPaperSource());
			currentSettings.setPrintColor(lastPrinterSetting.getPrintColor());
			currentSettings.setPrintQuality(lastPrinterSetting.getPrintQuality());
			currentSettings.setPrintResolution(lastPrinterSetting.getPrintResolution());
			currentSettings.setPrintSides(lastPrinterSetting.getPrintSides());
		}else{
			Printer layoutPrinter=(job.getPrinter() !=null)? job.getPrinter():Printer.getDefaultPrinter();
			PageLayout pageLayout = layoutPrinter.createPageLayout(Paper.A4, PageOrientation.PORTRAIT, 
					Printer.MarginType.DEFAULT);
			currentSettings.setPageLayout(pageLayout);
		}
		
    	if(job.showPrintDialog(null)){
    		lastPrinter=job.getPrinter();
    		lastPrinterSetting=job.getJobSettings();
    		//To make the webview aware of the CSS in head node, we need to do this. I admit that it looks very stupid.
    		String html=(String) templateWebView.getEngine().executeScript("document.documentElement.innerHTML");
    		Document doc = Jsoup.parse(html, "UTF-8");
    		Element element=doc.select("style").first();
	    	if(element !=null) element.html(element.html().replace("opacity:0.4", "display:none"));
    		templateWebView.getEngine().loadContent(doc.html());
    		//As the html file is internal, we can safely assume that the loading takes no time.
    		templateWebView.getEngine().print(job);
    		job.endJob();
    		element=doc.select("style").first();
    		if(element !=null) element.html(element.html().replace("display:none", "opacity:0.4"));
	    	templateWebView.getEngine().loadContent(doc.html());
    	} else Log("Printing is cancelled");	
	}
	
	private String convertToUTF8(String str){
		//Transcode the string into UTF-8
		try {
			String utf8Text=new String(str.getBytes("UTF-8"), "UTF-8");
			return utf8Text;
		} catch (java.io.UnsupportedEncodingException e2) {
			alert("Error","The inserted text is not supported. (Cannot transcode to UTF-8)");
		}
		return "";
	}
	
	/**
	 * Remove suffix of a file name, if no suffix is found, just return the input string
	 * @param fileName
	 * @return
	 */
	private static String removeSuffix(String fileName){
		return ( fileName !=null && !fileName.isEmpty() && fileName.contains(".") )? 
				fileName.substring(0,fileName.lastIndexOf('.')):fileName;
	}
	
	private void Log(String message){
		System.out.println(TAG+": "+message);
	}
	private void alert(String title, String message){
		if(mainActivity!=null) mainActivity.alert(title,message);
	}
}
