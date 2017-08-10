package com.rutgers.neemi;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.SignInButton;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Base64;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.j256.ormlite.dao.GenericRawResults;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.rutgers.neemi.model.Email;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import static android.app.Activity.RESULT_OK;


public class GmailActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {

    GoogleAccountCredential mCredential;
    private SignInButton gmailButton;
    ProgressDialog mProgress;

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;

    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = {GmailScopes.GMAIL_READONLY};
    DatabaseHelper helper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gmail);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar ab = getSupportActionBar();

        // Enable the Up button
        ab.setDisplayHomeAsUpEnabled(true);


        helper=new DatabaseHelper(this);

        //Google widgets
        gmailButton = (SignInButton) findViewById(R.id.gmailApiButton);
        gmailButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gmailButton.setEnabled(false);
                getResultsFromApi();
                gmailButton.setEnabled(true);
            }
        });



        mProgress = new ProgressDialog(this);
        mProgress.setMessage("Calling Gmail API ...");

        mCredential = GoogleAccountCredential.usingOAuth2(
                this, Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    Snackbar.make(findViewById(R.id.gcalCoordinatorLayout), "This app requires Google Play Services. Please install " +
                            "Google Play Services on your device and relaunch this app.", Snackbar.LENGTH_SHORT ).show();
                } else {
                    getResultsFromApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                this.getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        getResultsFromApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi();
                }
                break;
        }

    }

    /**
     * Attempt to call the API, after verifying that all the preconditions are
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the app will prompt the user as
     * appropriate.
     */
    private void getResultsFromApi() {
        if (!isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (!isDeviceOnline()) {
            Snackbar.make(findViewById(R.id.gmailCoordinatorLayout), "No network connection available", Snackbar.LENGTH_SHORT ).show();
        } else {
            new MakeRequestTask(mCredential).execute();
        }
    }


    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }


    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     *
     * @param requestCode  The request code passed in
     *                     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    /**
     * Callback for when a permission is granted using the EasyPermissions
     * library.
     *
     * @param requestCode The request code associated with the requested
     *                    permission
     * @param list        The requested permission list. Never null.
     */
    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        // Do nothing.
    }


    /**
     * Callback for when a permission is denied using the EasyPermissions
     * library.
     *
     * @param requestCode The request code associated with the requested
     *                    permission
     * @param list        The requested permission list. Never null.
     */
    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Checks whether the device currently has a network connection.
     *
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     *
     * @return true if Google Play Services is available and up to
     * date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }


    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     *
     * @param connectionStatusCode code describing the presence (or lack of)
     *                             Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }


    /**
     * An asynchronous task that handles the Google Calendar API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    private class MakeRequestTask extends AsyncTask<Void, Void, Integer> {

        private com.google.api.services.gmail.Gmail gmailService = null;
        private Exception mLastError = null;

        MakeRequestTask(GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            gmailService = new com.google.api.services.gmail.Gmail.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("Gmail API Android")
                    .build();
        }

        /**
         * Background task to call Google Calendar API.
         *
         * @param params no parameters needed for this task.
         */
        @Override
        protected Integer doInBackground(Void... params) {
            try {
                return getDataFromApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;

            }
        }


        /**
         * Fetch a list of the next 10 events from the primary calendar.
         *
         * @return List of Strings describing returned events.
         * @throws IOException
         */
        private int getDataFromApi() throws IOException {
            // List the next 10 events from the primary calendar.
//            DatabaseHelper dbHelper = new DatabaseHelper(getApplicationContext());
//            ConnectionSource connectionSource = new AndroidConnectionSource(dbHelper);
//            try {
//                TableUtils.dropTable(connectionSource, Email.class,false);
//                TableUtils.createTable(connectionSource, Email.class);
//            } catch (SQLException e) {
//                e.printStackTrace();
//            }

            RuntimeExceptionDao<Email, String> emailDao = helper.getEmailDao();

            String user = "me";
            int totalItemsInserted=0;
            String pageToken = null;
            Calendar cal = Calendar.getInstance(Calendar.getInstance().getTimeZone());
            cal.add(Calendar.MONTH, -6); // substract 6 months
            Long since=cal.getTimeInMillis()/1000;
            System.out.println("since = "+since);
            String timestamp = null;

            GenericRawResults<String[]> rawResults = emailDao.queryRaw("select max(timestamp) from Email;");
            List<String[]> results = null;
            try {
                results = rawResults.getResults();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            if (results!=null){
                String[] resultArray = results.get(0);
                System.out.println("timestamp= " + resultArray[0]);
                timestamp=resultArray[0];
            }


            if (timestamp!=null) {
                cal.setTimeInMillis(Long.parseLong(timestamp));
                since = cal.getTimeInMillis();
            }

            System.out.println("Since="+since);

            do {
                ListMessagesResponse response = gmailService.users().messages().list(user)
                        .setPageToken(pageToken)
                        .setQ("after:"+ since)
                        .execute();

                List<Message> messages = response.getMessages();
                pageToken = response.getNextPageToken();

                if (messages!=null){
                    for (int i = 0; i < messages.size(); i++) {
                        try {
                            Message msg = gmailService.users().messages().get(user, messages.get(i).get("id").toString()).execute();
                            List<MessagePart> parts = msg.getPayload().getParts();
                            List<MessagePartHeader> headers = msg.getPayload().getHeaders();
                            Email email = readParts(parts);
                            email.setTimestamp(System.currentTimeMillis() / 1000);
                            email.setId(msg.getId());
                            email.setThreadId(msg.getThreadId());
                            //email.setLabelIds(msg.getLabelIds());
                            email.setHistoryId(msg.getHistoryId());
                            email.setDate(new Date(msg.getInternalDate()));
                            for (MessagePartHeader header : headers) {
                                String name = header.getName();
                                if (name.equals("From") || name.equals("from")) {
                                    email.setFrom(header.getValue());
                                } else if (name.equals("To") || name.equals("to")) {
                                    email.setTo(header.getValue());
                                } else if (name.equals("Bcc") || name.equals("bcc")) {
                                    email.setBcc(header.getValue());
                                } else if (name.equals("Cc") || name.equals("cc")) {
                                    email.setCc(header.getValue());
                                }
                            }
                            emailDao.create(email);
                            totalItemsInserted++;
                            System.out.println("EmailsInserted = " + totalItemsInserted);
                        } catch (Exception e) {
                            System.out.println(e.getMessage());
                        }
                    }
                }
            }while(pageToken != null);

            System.out.println("EmailsInserted = " + totalItemsInserted);
            return totalItemsInserted;
        }


    private Email readParts(List<MessagePart> parts){
        Email email = new Email();
        for(MessagePart part:parts){
            try{
                String mime = part.getMimeType();
                if(mime.contentEquals("text/plain")){
                    String s = new String(Base64.decodeBase64(part.getBody().getData().getBytes()));
                    email.setTextContent(s);
                }else if(mime.contentEquals("text/html")){
                    String s = new String(Base64.decodeBase64(part.getBody().getData().getBytes()));
                    email.setHtmlContent(s);
                }else if(mime.contentEquals("multipart/alternative")){
                    List<MessagePart> subparts  =part.getParts();
                    Email subreader = readParts(subparts);
                    email.setHtmlContent(subreader.getHtmlContent());
                    email.setTextContent(subreader.getTextContent());
                }else if(mime.contentEquals("application/octet-stream")){
                    email.setHasAttachments(true);
                }

            }catch(Exception e){
                // get file here

            }

        }
        return email;
    }


    @Override
        protected void onPreExecute() {
            mProgress.show();
        }

        @Override
        protected void onPostExecute(Integer output) {
            mProgress.hide();

            Intent myIntent = new Intent(getApplicationContext(), MainActivity.class);
            myIntent.putExtra("key", "gmail");
            myIntent.putExtra("items", output);
            startActivity(myIntent);

//            if (output == 0) {
//                Snackbar.make(findViewById(R.id.gmailCoordinatorLayout), "No emails fetched.", Snackbar.LENGTH_SHORT ).show();
//            } else {
//                Snackbar.make(findViewById(R.id.gmailCoordinatorLayout), output+" emails fetched.", Snackbar.LENGTH_SHORT ).show();
//            }
        }

        @Override
        protected void onCancelled() {
            mProgress.hide();
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            GcalFragment.REQUEST_AUTHORIZATION);
                } else {
                    Snackbar.make(findViewById(R.id.gmailCoordinatorLayout), "The following error occurred:\n"
                            + mLastError.getMessage(), Snackbar.LENGTH_SHORT ).show();
                }
            } else {
                Snackbar.make(findViewById(R.id.gmailCoordinatorLayout), "Request cancelled"
                        + mLastError.getMessage(), Snackbar.LENGTH_SHORT ).show();
            }
        }


    }
}



