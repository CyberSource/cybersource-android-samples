package com.cybersource.inapp.fragments.encryption;


import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.visa.inappsdk.common.error.SDKError;
import com.visa.inappsdk.common.exceptions.SDKInvalidCardException;
import com.visa.inappsdk.connectors.inapp.InAppSDKApiClient;
import com.visa.inappsdk.connectors.inapp.receivers.TransactionResultReceiver;
import com.visa.inappsdk.connectors.inapp.transaction.client.InAppTransaction;
import com.visa.inappsdk.connectors.inapp.transaction.client.InAppTransactionType;
import com.visa.inappsdk.datamodel.response.SDKGatewayResponse;
import com.visa.inappsdk.datamodel.transaction.callbacks.SDKApiConnectionCallback;
import com.visa.inappsdk.datamodel.transaction.fields.SDKBillTo;
import com.visa.inappsdk.datamodel.transaction.fields.SDKCardAccountNumberType;
import com.visa.inappsdk.datamodel.transaction.fields.SDKCardData;
import com.cybersource.inapp.R;
import com.cybersource.inapp.services.MessageSignatureService;
import com.cybersource.inapp.signature.MessageSignature;

/**
 * Fragment for a test Encryption Transaction
 *
 * Created by fzubair on 11/19/2015.
 */
public class EncryptionFragment extends Fragment implements View.OnClickListener, SDKApiConnectionCallback {

    public static final String TAG = "EncryptionFragment";
    private final String ACCOUNT_NUMBER = "4111111111111111";
    private final String EXPIRATION_MONTH = "11";
    private final String EXPIRATION_YEAR = "2017";
    private final String CVV = "256";
    private final String POSTAL_CODE = "98001";
    public static String API_LOGIN_ID = "test_paymentech_001"; // replace with YOUR_API_LOGIN_ID
    private static String TRANSACT_NAMESPACE = "urn:schemas-cybersource-com:transaction-data-1.120";

    /** SOAP Payments TEST endpoint address. */
    public static String PAYMENTS_TEST_URL = "https://mobiletest.ic3.com/mpos/transactionProcessor/";
    /** SOAP Payments PROD endpoint address. */
    public static String PAYMENTS_PROD_URL = "https://mobile.ic3.com/mpos/transactionProcessor/";

    private final int MIN_CARD_NUMBER_LENGTH = 13;
    private final int MIN_YEAR_LENGTH = 2;
    private final int MIN_CVV_LENGTH = 3;
    private final String YEAR_PREFIX = "20";

    private Button checkoutButton;
    private EditText cardNumberView;
    private EditText monthView;
    private EditText yearView;
    private EditText cvvView;

    private ProgressDialog progressDialog;
    private RelativeLayout responseLayout;
    private TextView responseTitle;
    private TextView responseValue;

    private String cardNumber;
    private String month;
    private String year;
    private String cvv;

    InAppSDKApiClient apiClient;

    private TransactionResultReceiver resultReceiver;

    public EncryptionFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // build an InApp SDK Api client to make API calls.
        // parameters:
        // 1) Context - current context
        // 2) InAppSDKApiClient.Environment - CYBS ENVIRONMENT
        // 3) API_LOGIN_ID String - merchant's API LOGIN ID
        apiClient = new InAppSDKApiClient.Builder
                (getActivity(), InAppSDKApiClient.Environment.ENV_TEST, API_LOGIN_ID)
                .sdkConnectionCallback(this) // receive callbacks for connection results
                .sdkApiProdEndpoint(PAYMENTS_PROD_URL) // option to configure PROD Endpoint
                .sdkApiTestEndpoint(PAYMENTS_TEST_URL) // option to configure TEST Endpoint
                .sdkConnectionTimeout(5000) // optional connection time out in milliseconds
                .transactionNamespace(TRANSACT_NAMESPACE) // optional - ApiClient has a default namespace too
                .build();
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_encryption, container, false);

        cardNumberView = (EditText) view.findViewById(R.id.card_number_view);
        setUpCreditCardEditText();
        monthView = (EditText) view.findViewById(R.id.date_month_view);
        yearView = (EditText) view.findViewById(R.id.date_year_view);
        cvvView = (EditText) view.findViewById(R.id.security_code_view);

        checkoutButton = (Button) view.findViewById(R.id.button_checkout_order);
        checkoutButton.setOnClickListener(this);

        responseLayout =  (RelativeLayout) view.findViewById(R.id.response_layout);
        responseTitle =  (TextView) view.findViewById(R.id.encrypted_data_title);
        responseValue =  (TextView) view.findViewById(R.id.encrypted_data_view);
        return view;
    }

    @Override
    public void onPause() {
        super.onPause();
        // un-register the result receiver
        resultReceiver = null;
    }


    @Override
    public void onClick(View v) {
        if(!areFormDetailsValid())
            return;

        progressDialog = ProgressDialog.show(getActivity(), "Please Wait",
                "Encrypting Card Data...", true);
        if(responseLayout.getVisibility() == View.VISIBLE)
            responseLayout.setVisibility(View.GONE);

        InAppTransaction transactionObject = prepareEncryptTransactionObject();
        try {
            // make a call to connect to API
            // parameters:
            // 1) InAppSDKApiClient.Api - Type of API to make a request
            // 2) SDKTransactionObject - The transactionObject for the current transaction
            // 3) Signature String - fresh message signature for this transaction
            apiClient.performApi(InAppSDKApiClient.Api.API_ENCRYPTION,
                    transactionObject, generateSignature(transactionObject));
        } catch (NullPointerException e) {
            Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
            if(progressDialog.isShowing())
                progressDialog.dismiss();
        }
    }

    private boolean areFormDetailsValid(){
        cardNumber = cardNumberView.getText().toString().replace(" ", "");
        month = monthView.getText().toString();
        year =  YEAR_PREFIX + yearView.getText().toString();
        cvv = cvvView.getText().toString();

        if(isEmptyField()){
            checkoutButton.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.shake_error));
            Toast.makeText(getActivity(), "Empty fields", Toast.LENGTH_LONG).show();
            return false;
        }
        return validateFields();
    }

    private boolean isEmptyField(){
        return  (cardNumber != null && cardNumber.isEmpty()) || (month != null && month.isEmpty())
                || (year != null && year.isEmpty()) || (cvv != null && cvv.isEmpty());
    }

    private boolean validateFields() {
        if(cardNumber.length() < MIN_CARD_NUMBER_LENGTH){
            cardNumberView.requestFocus();
            cardNumberView.setError(getString(R.string.invalid_card_number));
            checkoutButton.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.shake_error));
            return false;
        }
        int monthNum = Integer.parseInt(month);
        if(monthNum < 1 || monthNum > 12){
            monthView.requestFocus();
            monthView.setError(getString(R.string.invalid_month));
            checkoutButton.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.shake_error));
            return false;
        }
        if(month.length() < MIN_YEAR_LENGTH){
            monthView.requestFocus();
            monthView.setError(getString(R.string.two_digit_month));
            checkoutButton.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.shake_error));
            return false;
        }
        if(year.length() < MIN_YEAR_LENGTH){
            yearView.requestFocus();
            yearView.setError(getString(R.string.invalid_year));
            checkoutButton.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.shake_error));
            return false;
        }
        if(cvv.length() < MIN_CVV_LENGTH){
            cvvView.requestFocus();
            cvvView.setError(getString(R.string.invalid_cvv));
            checkoutButton.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.shake_error));
            return false;
        }
        return true;
    }

    private void setUpCreditCardEditText() {
        cardNumberView.addTextChangedListener(new TextWatcher() {
            private boolean spaceDeleted;

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // check if a space was deleted
                CharSequence charDeleted = s.subSequence(start, start + count);
                spaceDeleted = " ".equals(charDeleted.toString());
            }

            public void afterTextChanged(Editable editable) {
                // disable text watcher
                cardNumberView.removeTextChangedListener(this);

                // record cursor position as setting the text in the textview
                // places the cursor at the end
                int cursorPosition = cardNumberView.getSelectionStart();
                String withSpaces = formatText(editable);
                cardNumberView.setText(withSpaces);
                // set the cursor at the last position + the spaces added since the
                // space are always added before the cursor
                cardNumberView.setSelection(cursorPosition + (withSpaces.length() - editable.length()));

                // if a space was deleted also deleted just move the cursor
                // before the space
                if (spaceDeleted) {
                    cardNumberView.setSelection(cardNumberView.getSelectionStart() - 1);
                    spaceDeleted = false;
                }

                // enable text watcher
                cardNumberView.addTextChangedListener(this);
            }

            private String formatText(CharSequence text) {
                StringBuilder formatted = new StringBuilder();
                int count = 0;
                for (int i = 0; i < text.length(); ++i) {
                    if (Character.isDigit(text.charAt(i))) {
                        if (count % 4 == 0 && count > 0)
                            formatted.append(" ");
                        formatted.append(text.charAt(i));
                        ++count;
                    }
                }
                return formatted.toString();
            }
        });
    }

/*    private void setUpZipCodeEditText() {
        zipCodeView.addTextChangedListener(new TextWatcher() {
            private boolean dashDeleted;

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // check if a '-' was deleted
                CharSequence charDeleted = s.subSequence(start, start + count);
                dashDeleted = "-".equals(charDeleted.toString());
            }

            public void afterTextChanged(Editable editable) {
                // disable text watcher
                zipCodeView.removeTextChangedListener(this);

                // record cursor position as setting the text in the textview
                // places the cursor at the end
                int cursorPosition = zipCodeView.getSelectionStart();
                String withSpaces = formatText(editable);
                zipCodeView.setText(withSpaces);
                // set the cursor at the last position + the spaces added since the
                // space are always added before the cursor
                zipCodeView.setSelection(cursorPosition + (withSpaces.length() - editable.length()));

                // if a '-' was deleted also deleted just move the cursor
                // before the space
                if (dashDeleted) {
                    zipCodeView.setSelection(zipCodeView.getSelectionStart() - 1);
                    dashDeleted = false;
                }

                // enable text watcher
                zipCodeView.addTextChangedListener(this);
            }

            private String formatText(CharSequence text) {
                StringBuilder formatted = new StringBuilder();
                int count = 0;
                for (int i = 0; i < text.length(); ++i) {
                    if (Character.isDigit(text.charAt(i))) {
                        if (count % 5 == 0 && count > 0)
                            formatted.append("-");
                        formatted.append(text.charAt(i));
                        ++count;
                    }
                }
                return formatted.toString();
            }
        });
    }*/

    private SDKCardData prepareTestCardData() {
        SDKCardData cardData = null;
        try {
            cardData = new SDKCardData.Builder(ACCOUNT_NUMBER, EXPIRATION_MONTH,
                    EXPIRATION_YEAR)
                    .cvNumber(CVV) // optional
                    .type(SDKCardAccountNumberType.PAN) //optional - if token then not optional and must be set to SDKCardType.TOKEN
                    .build();
        } catch (SDKInvalidCardException e) {
            // Handle exception if the card is invalid
            Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
        return cardData;
    }

    private SDKCardData prepareCardDataFromFields() {
        SDKCardData cardData = null;
        try {
            cardData = new SDKCardData.Builder(cardNumber, month, year)
                    .cvNumber(cvv) // optional
                    .type(SDKCardAccountNumberType.PAN) //optional - if token, this must be set to SDKCardType.TOKEN
                    .build();
        } catch (SDKInvalidCardException e) {
            // Handle exception if the card is invalid
            Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
        return cardData;
    }

    private SDKBillTo prepareBillingInformation(){
        SDKBillTo billTo = new SDKBillTo.Builder("First Name", "Last Name")
                .postalCode("98052")
                .build();
        return billTo;
    }

    /**
     * prepares a transaction object to be used with the Gateway Encryption transaction
     */
    private InAppTransaction prepareEncryptTransactionObject() {
        // create a transaction object by calling the predefined api for creation
        return InAppTransaction.
                createTransactionObject(InAppTransactionType.IN_APP_TRANSACTION_ENCRYPTION) // type of transaction object
                .merchantReferenceCode("Android_InApp_Sample_Code" + "_" + Long.toString(System.currentTimeMillis())) // can be set to anything meaningful
                .cardData(prepareCardDataFromFields()) // card data to be encrypted
                .billTo(prepareBillingInformation()) // billing information
                .build();
    }

    /**
     * prepares a transaction object to be used with the Gateway Android Pay transaction
     */
    private InAppTransaction prepareAndroidPayTransactionObject() {
        // create a transaction object by calling the predefined api for creation
        return InAppTransaction.
                createTransactionObject(InAppTransactionType.IN_APP_TRANSACTION_ANDROID_PAY) // type of transaction object
                .merchantReferenceCode("Android_InApp_Sample_Code" + "_" + Long.toString(System.currentTimeMillis())) // can be set to anything meaningful
                .cardData(prepareCardDataFromFields()) // card data to be encrypted
                .billTo(prepareBillingInformation()) // billing information
                .build();
    }

    /**
     * Generates the signature in the sdk to be used with the next call
     */
    private String generateSignature(InAppTransaction transactionObject) {
        return MessageSignature.getInstance().generateSignature
                (transactionObject, API_LOGIN_ID);
    }

    @Override
    public void onErrorReceived(SDKError error) {
        if(responseLayout.getVisibility() != View.VISIBLE)
            responseLayout.setVisibility(View.VISIBLE);
        if(progressDialog.isShowing())
            progressDialog.dismiss();
        responseTitle.setText(R.string.error);
        responseValue.setText(getString(R.string.code) + error.getErrorCode()
                + "\n" +getString(R.string.message) + error.getErrorMessage().toString()
                + "\n" +getString(R.string.extra_message) + error.getErrorExtraMessage().toString());
        Log.d(TAG, "onErrorReceived > " + error.getErrorExtraMessage().toString());
    }

    @Override
    public void onApiConnectionFinished(SDKGatewayResponse response) {
        if(responseLayout.getVisibility() != View.VISIBLE)
            responseLayout.setVisibility(View.VISIBLE);
        if(progressDialog.isShowing())
            progressDialog.dismiss();
        responseTitle.setText(R.string.encrypted_card_data);
        responseValue.setText(getString(R.string.decision) + response.getDecision()
                + "\n" +getString(R.string.encrypted_data) + response.getEncryptedPaymentData());
        Log.d(TAG, "onApiConnectionFinished > " + response.getDecision());
    }

    public void onReceiveResult(int resultCode, Bundle resultData) {
        switch (resultCode){
            case MessageSignatureService.SERVICE_RESULT_CODE_SDK_RESPONSE:
                SDKGatewayResponse response = (SDKGatewayResponse) resultData
                        .getParcelable(MessageSignatureService.SERVICE_RESULT_RESPONSE_KEY);
                break;
            case MessageSignatureService.SERVICE_RESULT_CODE_SDK_ERROR:
                SDKError error = (SDKError) resultData
                        .getSerializable(MessageSignatureService.SERVICE_RESULT_ERROR_KEY);
                break;
        }
    }
}
