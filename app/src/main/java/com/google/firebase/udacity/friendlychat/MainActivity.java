
package com.google.firebase.udacity.friendlychat;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.firebase.FirebaseApiNotAvailableException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;
    private String mUsername;
    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    private static final int RC_SIGN_IN = 1;
    private static final int RC_PHOTO_PICKER=2;
    /**
     * All the Firebase Variables are Declared below
     * */
    //The entry point for the Firebase Realtime Database
    private FirebaseDatabase mFirebaseDatabase;
    //This class object refers to a specific part of the database.
    //In our case the 'mMessagesDatabaseReference' refers to the message portion of the Database
    private DatabaseReference mMessagesDatabaseReference;
    // Event Listener which provides call backs on various types of changes which happened in Database
    private ChildEventListener mChildEventListener;
    // FireBase Authentication instance variables. It is the entry point of Firebase Authentication SDK
    private FirebaseAuth mFirebaseAuth;
    //Event Listener which triggers when the Authentication State Changes. Like Signing in and Signing out
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    //Firebase Storage entry point
    private FirebaseStorage mFirebaseStorage;
    //Refers to specific part of the Firebase Storage
    private StorageReference mChatPhotosStorageReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mUsername = ANONYMOUS;
        /**
         * Initialize Firebase Variables
         * */
        //Entry point for the Database
        mFirebaseDatabase = FirebaseDatabase.getInstance();
        // mFirebaseDatabase.getReference() gives us the root node, then we go for the child node "messages".
        mMessagesDatabaseReference = mFirebaseDatabase.getReference().child("messages");
        mFirebaseStorage = FirebaseStorage.getInstance();
        //mFirebaseStorage.getReference gives us root folder, then we go for the child folder "chat_photos"
        mChatPhotosStorageReference = mFirebaseStorage.getReference().child("chat_photos");

        //Initialize the mFirebaseAuth variable
        mFirebaseAuth = FirebaseAuth.getInstance();

        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        final List<FriendlyMessage> friendlyMsgsArray = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMsgsArray);
        mMessageListView.setAdapter(mMessageAdapter);
        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY,true);
                startActivityForResult(Intent.createChooser(intent,"Complete action using"),RC_PHOTO_PICKER);
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                    mSendButton.setTextColor(Color.parseColor("#000000"));
                } else {
                    mSendButton.setEnabled(false);
                }
            }
            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Send messages on click
                FriendlyMessage mFriendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(),mUsername,null);
                mMessagesDatabaseReference.push().setValue(mFriendlyMessage);
                // Clear input box
                mMessageEditText.setText("");
            }
        });

        //Initialize the Firabase Variable which listnes to change in Authentication of user
        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            //parameter firebaseAuth contains whether the user is Authenticated or not at this particular time or not
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if( user != null){
                    //user is signed in
                    //Calling a method which will update the Username from Anonymous to Username from SignIn
                    onSignedInInitialize(user.getDisplayName());
                }
                else {
                    //user is signed out
                    // As the user is not signed in we need to detach the ChildListeners, clear the msgs on screen
                    // It is done cuz the Anonymous should no longer see the updates and already existing msgs
                    onSignedOutCleanUp();
                    //Starts the intent which shows various Sign-In Options
                    startActivityForResult(
                        AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .setIsSmartLockEnabled(false)
                            .setAvailableProviders(Arrays.asList(
                                //new AuthUI.IdpConfig.EmailBuilder().build(),
                                //new AuthUI.IdpConfig.PhoneBuilder().build(),
                                new AuthUI.IdpConfig.GoogleBuilder().build()//,
                                //new AuthUI.IdpConfig.FacebookBuilder().build(),
                                //new AuthUI.IdpConfig.TwitterBuilder().build()
                                ))
                            .build(),
                    RC_SIGN_IN);
                }
            }
        };
    }

    /*In the Default app, without overriding this method, when the user is in Sign In options page and presses the back button it reloads the SignIN
     * options screen and creates an infinite loop making the user unable to exit the app by pressing the back button without Signing in */
    /*This can be fixed by Overriding the foll. method which creates the Sign In options screen*/
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        //Don't know RC_SIGN_IN
        if (requestCode == RC_SIGN_IN) {
            // This method returns two variables RESULT_OK and RESULT_CANCELLED
            /*RESULT_OK means the user has been authenticated successfully*/
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Sign In Successful", Toast.LENGTH_SHORT).show();
            }
            else if(resultCode == RESULT_CANCELED){
                Toast.makeText(this, "Sign In Cancelled", Toast.LENGTH_SHORT).show();
                finish();
            }
            else if(requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK){
                //data.getData will give us the Uri for the
                Uri selectImageUri = data.getData();
                //For this check out Link
                // "https://classroom.udacity.com/courses/ud0352/lessons/fab5bf81-cfe5-4071-9afe-b6eb324a6257/concepts/78897c9b-c719-4393-a7a1-a2f3af334b7c"
                StorageReference chatPhotoRef = mChatPhotosStorageReference.child(selectImageUri.getLastPathSegment());
            }
        }
    }

    private void onSignedInInitialize(String displayName) {
        mUsername = displayName;
        attachFirebaseEventListener(mChildEventListener);
    }

    /*
    This method is called when the user is not Signed In.
    It clears up the screen and clears the name of listener along with removing the Child Event Listener
    */
    private void onSignedOutCleanUp(){
        //Setting the username to default as the user is signed out now
        mUsername = ANONYMOUS;
        mMessageAdapter.clear();
        //Detaches the Child Event Listener
        detachFirebaseEventListener(mChildEventListener);
    }
//This method detaches the Child Listener which listens to various changes to the database like creating, moving, deletion etc
    private void detachFirebaseEventListener(ChildEventListener childEventListener) {
        mChildEventListener = childEventListener;
        if(mChildEventListener != null){
            mMessagesDatabaseReference.removeEventListener(mChildEventListener);
            mChildEventListener = null;
        }
    }

//This method attaches the Child Listener which listens to various changes to the database like creating, moving, deletion etc
    private void attachFirebaseEventListener(ChildEventListener childEventListener){
        mChildEventListener = childEventListener;
        if(mChildEventListener == null){
            mChildEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                    FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
                    mMessageAdapter.add(friendlyMessage);
                }
                @Override
                public void onChildChanged(DataSnapshot dataSnapshot, String s) {}
                @Override
                public void onChildRemoved(DataSnapshot dataSnapshot) {}
                @Override
                public void onChildMoved(DataSnapshot dataSnapshot, String s) {}
                @Override
                public void onCancelled(DatabaseError databaseError) {}
            };
            mMessagesDatabaseReference.addChildEventListener(mChildEventListener);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.sign_out_menu:
                AuthUI.getInstance().signOut(this);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        /* Attaching the Authentication Listener in On resume method is the best way to attach as it is called every time the app resumes, restarts */
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //It is necessary to remove the Authentication Listener when the activity is in pause state.
        //Cuz there is no need for checking the authentication when the app is in background mode
        if(mFirebaseAuth != null){
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }
        //Detach the child Listener
        detachFirebaseEventListener(mChildEventListener);
    }

}