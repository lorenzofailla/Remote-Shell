package apps.java.loref;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.eclipse.swt.SWT;
import org.eclipse.wb.swt.SWTResourceManager;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseCredentials;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.DatabaseReference.CompletionListener;

import apps.java.loref.RemoteCommand;
import apps.java.loref.ReplyPrefix;

public class MainWindow {

	private String JSON_AUTH_LOCATION = "C:\\Users\\lore_f\\Downloads\\domotic-28a5e-firebase-adminsdk-vxto2-9037ff05a1.json";
	private String DATABASE_URL = "https://domotic-28a5e.firebaseio.com/";

	protected Shell shell;
	private Text text;

	private String thisDevice = "Vaio-W7";
	private String remoteDevice = "lorenzofailla-home";
	private String userName = "lorenzofailla";
	private DatabaseReference shellResponse;
	
	private DatabaseReference messagesToRemoteDevice;

	private boolean initMsgSent = false;
	private boolean sshShellReady = false;
	private boolean isConnected = true;

	private String mainTextBoxContent;
	private ByteArrayOutputStream out = new ByteArrayOutputStream();

	private class ShellComm extends Thread {

		
		
		public ShellComm() {
		};
		
		

		public void run() {
			// inizia la connessione al database
			mainTextBoxContent = "Connecting to Firebase Database...";

			Display.getDefault().syncExec(new Runnable() {

				@Override
				public void run() {
					text.setText(mainTextBoxContent);

				}

			});

			if (connectToFirebaseDatabase()) {
				mainTextBoxContent += "\nSuccessfully connected.";

			} else {
				mainTextBoxContent += "\nConnection error.";
			}

			refreshTextBox();

			messagesToRemoteDevice = FirebaseDatabase.getInstance()
					.getReference("/Users/" + userName + "/Devices/" + remoteDevice + "/IncomingCommands");
			DatabaseReference messagesFromRemoteDevice = FirebaseDatabase.getInstance()
					.getReference("/Users/" + userName + "/Devices/" + thisDevice + "/IncomingCommands");

			messagesFromRemoteDevice.addChildEventListener(new ChildEventListener() {

				@Override
				public void onChildRemoved(DataSnapshot snapshot) {
					// TODO Auto-generated method stub

				}

				@Override
				public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
					// TODO Auto-generated method stub

				}

				@Override
				public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
					// TODO Auto-generated method stub

				}

				@Override
				public void onChildAdded(DataSnapshot snapshot, String previousChildName) {

					GenericTypeIndicator<RemoteCommand> t = new GenericTypeIndicator<RemoteCommand>() {
					};
					RemoteCommand message = snapshot.getValue(t);

					switch (message.getHeader()) {
					case "SSH_SHELL_READY":
						sshShellReady = true;
						messagesFromRemoteDevice.child(snapshot.getKey()).removeValue();

						break;

					}

				}

				@Override
				public void onCancelled(DatabaseError error) {
					// TODO Auto-generated method stub

				}
			});

			mainTextBoxContent += "\nInitializing SSH session...";
			refreshTextBox();

			RemoteCommand message = new RemoteCommand("__initialize_ssh", "null", thisDevice);
			messagesToRemoteDevice.child("" + System.currentTimeMillis()).setValue(message, new CompletionListener() {

				@Override
				public void onComplete(DatabaseError error, DatabaseReference ref) {

					initMsgSent = true;
				}

			});

			mainTextBoxContent += "\nSending request...";
			refreshTextBox();

			while (!initMsgSent) {

				try {
					this.sleep(100);

				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
			mainTextBoxContent += "Done.\nWaiting for reply...";
			refreshTextBox();

			while (!sshShellReady) {

				try {
					this.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}

			mainTextBoxContent += "\nShell ready";
			refreshTextBox();

			String databaseNode = "/Users/" + userName + "/Devices/" + remoteDevice + "/SSHShells/" + thisDevice;
			shellResponse = FirebaseDatabase.getInstance().getReference(databaseNode);
			shellResponse.addChildEventListener(new ChildEventListener() {

				@Override
				public void onChildRemoved(DataSnapshot snapshot) {

				}

				@Override
				public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
				}

				@Override
				public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
					//
					
					String support=snapshot.getValue().toString().replace('\u0000', ' ');
					mainTextBoxContent = support;
					refreshTextBox();
					
				}

				@Override
				public void onChildAdded(DataSnapshot snapshot, String previousChildName) { // TODO
					//
					String support=snapshot.getValue().toString().replace('\u0000', ' ');
					mainTextBoxContent = support;
					refreshTextBox();
					
				}

				@Override
				public void onCancelled(DatabaseError error) {
				}

			});

			mainTextBoxContent += "\nListener initialized";
			refreshTextBox();

		}

	};

	/**
	 * Launch the application.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			MainWindow window = new MainWindow();
			window.open();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Open the window.
	 */
	public void open() {
		Display display = Display.getDefault();
		createContents();
		shell.open();
		shell.layout();

		display.addFilter(SWT.KeyDown, new Listener() {
			@Override
			public void handleEvent(Event event) {
				
				switch(event.keyCode){
				
				case 13:
					out.write('\n');
					sendToConsole();
					break;
					
				default:
					out.write(event.character);
				}

			}

		});

		ShellComm shellComm = new ShellComm();
		shellComm.start();
		
		

		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}

		}
		
		disconnectShell();
		
		while(isConnected){
			
			try {
				System.out.println("waiting for disconnection");
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		
		System.exit(0);
		

	}

	private void disconnectShell() {

		DatabaseReference messagesToRemoteDevice = FirebaseDatabase.getInstance()
				.getReference("/Users/" + userName + "/Devices/" + remoteDevice + "/IncomingCommands");
		RemoteCommand message = new RemoteCommand("__close_ssh", "null", thisDevice);
		messagesToRemoteDevice.child("" + System.currentTimeMillis()).setValue(message, new CompletionListener() {

			@Override
			public void onComplete(DatabaseError error, DatabaseReference ref) {
				System.out.println("termination message sent");
				isConnected=false;

			}

		});

	}

	/**
	 * Create contents of the window.
	 */
	protected void createContents() {
		shell = new Shell();
		shell.setSize(640, 480);
		shell.setText("SWT Application");

		text = new Text(shell, SWT.BORDER | SWT.MULTI);
		text.setEditable(false);
		text.setFont(SWTResourceManager.getFont("Consolas", 10, SWT.NORMAL));
		text.setBounds(5, 5, 630, 470);

	}

	private boolean connectToFirebaseDatabase() {

		File jsonAuthFileLocation = new File(JSON_AUTH_LOCATION);
		try {
			FileInputStream serviceAccount = new FileInputStream(jsonAuthFileLocation);
			FirebaseOptions options = new FirebaseOptions.Builder()
					.setCredential(FirebaseCredentials.fromCertificate(serviceAccount)).setDatabaseUrl(DATABASE_URL)
					.build();

			FirebaseApp.initializeApp(options);

			serviceAccount.close();

			return true;

		} catch (IOException e) {

			return false;

		}

	}

	private void refreshTextBox() {

		Display display = Display.getDefault();
		display.syncExec(new Runnable() {

			@Override
			public void run() {
				System.out.println("------------------");
				text.setText(mainTextBoxContent);
				System.out.println(mainTextBoxContent);
				
			}

		});

	}
	
	private void sendToConsole(){
		
		if(messagesToRemoteDevice!=null && out!=null){
			
			try {
				out.flush();
				messagesToRemoteDevice.child(""+System.currentTimeMillis()).setValue(new RemoteCommand("__ssh_input_command",out.toString(), thisDevice));
				out=new ByteArrayOutputStream();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		
		}
		
	}

}
