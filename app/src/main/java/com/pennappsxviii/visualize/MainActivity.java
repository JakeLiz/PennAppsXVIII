package com.pennappsxviii.visualize;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;

import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.document.FirebaseVisionDocumentText;
import com.google.firebase.ml.vision.document.FirebaseVisionDocumentTextRecognizer;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;
import com.google.firebase.ml.vision.text.RecognizedLanguage;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Currency;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    public static final int MY_PERMISSIONS_REQUEST_CAMERA = 100;
    public static final int REQUEST_IMAGE_CAPTURE = 1;
    public static final String ALLOW_KEY = "ALLOWED";
    public static final String CAMERA_PREF = "camera_pref";

    private ArrayList<String> keywords;
    private String imageText;
    private String mCurrentPhotoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (getFromPref(this, ALLOW_KEY)) {
                showSettingsAlert();
            } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                    showAlert();
                } else {
                    // Request Permission
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, MY_PERMISSIONS_REQUEST_CAMERA);
                }
            }
        } else {
            openCamera();
        }
    }

    // Preferences & Permissions
    public static void saveToPreferences(Context context, String key, Boolean allowed) {
        SharedPreferences myPrefs = context.getSharedPreferences(CAMERA_PREF, Context.MODE_PRIVATE);
        SharedPreferences.Editor prefsEditor = myPrefs.edit();
        prefsEditor.putBoolean(key, allowed);
        prefsEditor.commit();
    }

    public static Boolean getFromPref(Context context, String key) {
        SharedPreferences myPrefs = context.getSharedPreferences(CAMERA_PREF, Context.MODE_PRIVATE);
        return (myPrefs.getBoolean(key, false));
    }

    public static void startInstalledAppDetailsActivity(final Activity context) {
        if (context == null) {
            return;
        }
        final Intent i = new Intent();
        i.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.addCategory(Intent.CATEGORY_DEFAULT);
        i.setData(Uri.parse("package:" + context.getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        context.startActivity(i);
    }

    private void showSettingsAlert() {
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle("Alert");
        alertDialog.setMessage("Visualize needs to access the Camera.");

        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "DONT ALLOW", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                finish();
            }
        });

        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "SETTINGS", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                startInstalledAppDetailsActivity(MainActivity.this);
            }
        });
        alertDialog.show();
    }

    private void showAlert() {
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle("Alert");
        alertDialog.setMessage("Visualize needs to access the camera");

        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "DONT ALLOW", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                finish();
            }
        });

        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "ALLOW", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, MY_PERMISSIONS_REQUEST_CAMERA);
            }
        });
        alertDialog.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_CAMERA: {
                for (int i = 0, len = permissions.length; i < len; i++) {
                    String permission = permissions[i];
                    if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        boolean showRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
                        if (showRationale) {
                            showAlert();
                        } else if (!showRationale) {
                            saveToPreferences(MainActivity.this, ALLOW_KEY, true);
                        }
                    }
                }
            }
        }
    }

    // Initiate Camera && Declare Storage Location
    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.android.fileprovider",
                        photoFile);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    // Take Picture
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",    /* suffix */
                storageDir      /* directory */
        );

        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            textRecognition();
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
    }
    @Override
    public void onBackPressed() {
        openCamera();
    }

    // Image To Text
    public void textRecognition() {
        Bitmap bitmapImage = BitmapFactory.decodeFile(mCurrentPhotoPath);
        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(bitmapImage);
        FirebaseVisionTextRecognizer textRecognizer = FirebaseVision.getInstance().getOnDeviceTextRecognizer();

        textRecognizer.processImage(image)
            .addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
                @Override
                public void onSuccess(FirebaseVisionText result) {
                    // Task completed successfully
                    imageText = result.getText();
                    for (FirebaseVisionText.TextBlock block: result.getTextBlocks()) {
                        String blockText = block.getText();
                        Float blockConfidence = block.getConfidence();
                        List<RecognizedLanguage> blockLanguages = block.getRecognizedLanguages();
                        Point[] blockCornerPoints = block.getCornerPoints();
                        Rect blockFrame = block.getBoundingBox();
                        for (FirebaseVisionText.Line line : block.getLines()) {
                            String lineText = line.getText();
                            Float lineConfidence = line.getConfidence();
                            List<RecognizedLanguage> lineLanguages = line.getRecognizedLanguages();
                            Point[] lineCornerPoints = line.getCornerPoints();
                            Rect lineFrame = line.getBoundingBox();
                            for (FirebaseVisionText.Element element : line.getElements()) {
                                String elementText = element.getText();
                                Float elementConfidence = element.getConfidence();
                                List<RecognizedLanguage> elementLanguages = element.getRecognizedLanguages();
                                Point[] elementCornerPoints = element.getCornerPoints();
                                Rect elementFrame = element.getBoundingBox();
                            }
                        }
                    }
                    Log.d("QWERTY", imageText);
                    rake();
                    flashcardDisplay();
                }
            })
            .addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    // Task failed with an exception
                    AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                    alertDialog.setTitle("Alert");
                    alertDialog.setMessage("Invalid Image");
                    alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Cancel", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    alertDialog.show();
                }
            });
    }

    // Rapid Automatic Keyword Extraction
    private void rake (){
        keywords = new ArrayList<String>();
        LinkedHashMap<String, Double> results = new Rake("en").getKeywordsFromText(imageText);

        Log.d("QWERTY", results + "");
        List<String> keys = new ArrayList<String>(results.keySet());
        Log.d("QWERTY", keys + "");
        for(int i = 0; i < (int)(Math.random()*7 + 5); i++){
            if(keys.size() != 0) keywords.add(keys.remove(0));
        }

        // Hardcoded Keywords Crosscheck Dictionary
        String[] templateArray = new String[]{"linear", "bent", "trigonal planar", "tetrahedral", "seesaw", "t-shape", "square pyramidical",
                "trigonal bipyramidical", "octahedral", "pyramidical",
                "adamantine","ammonia", "atom", "bicarbonate", "carbon_dioxied", "carbon_monoxide", "carbonix_acid", "carboxylic_acid",
                "ethanol", "isopropylchloride", "nanotube", "nitric acid", "phenols", "theobromine", "water"};
        int arrayIndex = keywords.size() - 1;
        for(String str : templateArray){
            if(imageText.contains(str) && arrayIndex >= 0){
                keywords.set(arrayIndex--, str);
            }
        }
        Collections.reverse(keywords);
    }

    // Text Display
    private void flashcardDisplay () {
        ArrayList<LinearLayout> layoutPackage = new ArrayList<LinearLayout>();
        // Layout Package
        for(int i = 0; i < keywords.size(); i++) {
            String str = keywords.get(i);

            final LinearLayout tempLayout = new LinearLayout(this);
            tempLayout.setOrientation(LinearLayout.VERTICAL);
            tempLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tempLayout.setGravity(Gravity.CENTER);

            final Button tempButton = new Button(this);
            tempButton.setLayoutParams(new LinearLayout.LayoutParams(Resources.getSystem().getDisplayMetrics().widthPixels/2, Resources.getSystem().getDisplayMetrics().widthPixels/2));
            tempButton.setForegroundGravity(Gravity.CENTER);

            tempButton.setBackgroundColor(Color.rgb((int)(Math.random()*100), (int)(Math.random()*100), (int)(Math.random()*100)));
            tempButton.setText(str);
            tempButton.setTextSize(20);
            tempButton.setTextColor(Color.WHITE);
            tempButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(MainActivity.this, ModelViewerActivity.class);
                    intent.putExtra("modelName", str.replace(" ","_")+".stl");
                    MainActivity.this.startActivity(intent);
                }
            });
            tempLayout.addView(tempButton);
            layoutPackage.add(tempLayout);
        }
        // Scroll
        ScrollView view = new ScrollView(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        // Establish Three Vertical Regions Of Equal Weight
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.HORIZONTAL);
        main.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        main.setWeightSum(3f);
        LinearLayout col1 = new LinearLayout(this);
        col1.setOrientation(LinearLayout.VERTICAL);
        col1.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        col1.setGravity(Gravity.CENTER);
        // Loop To Add Internal Layouts To Column
        for(int i = 0; i < layoutPackage.size(); i+=2) col1.addView(layoutPackage.get(i));
        main.addView(col1);
        LinearLayout col2 = new LinearLayout(this);
        col2.setOrientation(LinearLayout.VERTICAL);
        col2.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        col2.setGravity(Gravity.CENTER);
        // Loop To Add Internal Layouts To Column
        for(int i = 1; i < layoutPackage.size(); i+=2) col2.addView(layoutPackage.get(i));
        main.addView(col2);
        view.addView(main);
        setContentView(view);
    }
}