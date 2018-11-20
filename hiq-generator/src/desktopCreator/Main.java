package desktopCreator;
	
import java.io.IOException;

import javafx.application.Application;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;


public class Main extends Application {
	private String TAG="Main Activity";
	private Stage stage;
	@Override
	public void start(Stage primaryStage) {
		primaryStage.setTitle("Authenticated 2D barcode Creator");
		stage=primaryStage;
		//useFXMLScene("FrontPage.fxml");
		FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("FrontPage.fxml"));
		try{
			Parent page=fxmlLoader.load();
			Scene scene = new Scene(page);
		    stage.setScene(scene);
		    stage.sizeToScene();
		    stage.show();
		    FrontPageController controller = (FrontPageController) fxmlLoader.getController();
			if(controller ==null) Log("Why null controller?");
			else controller.mainActivity=this;
		} catch (IOException e) {
			Log("Cannot read the default FXML page"+e.getLocalizedMessage()+e.getMessage()+e.getCause()+e.getCause().getCause());
		}
	}
	
	public static void main(String[] args) {
		launch(args);
	}
	
/*	private Parent useFXMLScene(String fxmlName){
		Parent page=null;
		try {
			page = (Parent) FXMLLoader.load(getClass().getResource(fxmlName), null, new JavaFXBuilderFactory());
			Scene scene = new Scene(page);
		    stage.setScene(scene);
		    stage.sizeToScene();
		    stage.show();
		} catch (IOException e) {
			Log("IO Exception on changing scene. The fxml concerned:"+fxmlName);
		}
		return page;
	}
*/
	/**
	 * Create a simple thread blocking dialog
	 * @param title
	 * @param message
	 */
	public void alert(String title, String message){
		alertDialog dia=new alertDialog(title,message,stage);
		dia.showDialog();
	}
	
	public Dialog getNewDialog(String title, Scene scene){
		return new Dialog("Reading the private key",stage,scene);
	}
	
	/**
	 * Creating a standard dialog window.
	 * Notice that this dialog window block the code execution. See the page of Stage.showAndWait() for more result.
	 * @author Solon Li
	 *
	 */
	static class Dialog extends Stage {
		public Dialog(){ }
	    public Dialog(String title, Stage owner, Scene scene) {
	    	initDialog(title,owner);
	        setScene(scene);        
	    }
	    protected void initDialog(String title, Stage owner){
			setTitle( title );
	        initStyle( StageStyle.UTILITY );
	        initModality( Modality.WINDOW_MODAL );
	        initOwner( owner );
	        setResizable( false );
		}
	    /**
	     * Show the dialog on the center of the owner window.
	     * Note: It is thread-blocking, i.e. the code after calling showDialog() will be executed after the event loop containing dialog.hide() is executed
	     */
		public void showDialog() {
	        sizeToScene();
	        centerOnScreen();
	        showAndWait();
	    }
	}
	/**
	 * Based on the standard dialog window, provide easy way to make an alert dialog
	 * @author Solon Li
	 *
	 */
	static class alertDialog extends Dialog {
		public alertDialog( String title, String message, Stage owner) {
			initDialog(title,owner);
	    	GridPane grid = new GridPane();
			grid.setAlignment(Pos.CENTER);
			grid.setHgap(10);
			grid.setVgap(10);
			grid.setPadding(new Insets(25, 25, 25, 25));
			Label messageLabel = new Label(message);
			messageLabel.setWrapText(true);
			grid.add(messageLabel, 0, 0, 1, 1); //column 0, row 0, span 1 column and 1 row only
	    	Button button = new Button("OK");
	    	grid.add(button, 0, 1, 1, 1); 
	    	GridPane.setHalignment(button, HPos.CENTER);
	    	setScene( new Scene(grid) );
	    	final alertDialog dialog=this;
	    	button.setOnAction(new EventHandler<ActionEvent>() {
	        	@Override
	            public void handle(ActionEvent event) {
            	    dialog.close();
	            }
	        });
		}
	}
	
	public void Log(String message){
		System.out.println(TAG+": "+message);
	}
}
