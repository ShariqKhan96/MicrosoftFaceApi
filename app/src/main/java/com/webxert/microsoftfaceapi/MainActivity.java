package com.webxert.microsoftfaceapi;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.face.contract.CreatePersonResult;
import com.microsoft.projectoxford.face.contract.Face;
import com.microsoft.projectoxford.face.contract.FaceRectangle;
import com.microsoft.projectoxford.face.contract.IdentifyResult;
import com.microsoft.projectoxford.face.contract.Person;
import com.microsoft.projectoxford.face.contract.TrainingStatus;
import com.microsoft.projectoxford.face.rest.ClientException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    ImageView imageView;
    Button button;
    private FaceServiceRestClient faceServiceRestClient = new FaceServiceRestClient("https://westcentralus.api.cognitive.microsoft.com/face/v1.0", "7c73377533b04d21a99310908f9e008e");
    String personGroupId = "1d";
    Face facesDetected[];
    Bitmap bitmap;

    String names = "";


    TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        textView = findViewById(R.id.name);
        bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.oned_2);
        imageView = findViewById(R.id.imageView);
        imageView.setImageBitmap(bitmap);
        findViewById(R.id.detect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                detectAndFrame(bitmap);
//                Thread thread = new Thread(new Runnable() {
//                    @Override
//                    public void run() {
//                        try {
//                            TrainingStatus trainingStatus = faceServiceRestClient.getPersonGroupTrainingStatus("xyz");
//                            Log.e("trainingstatus", trainingStatus.status + " ");
//
//                        } catch (ClientException e) {
//                            e.printStackTrace();
//                            Log.e(MainActivity.class.getSimpleName(), e.getMessage());
//
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                            Log.e(MainActivity.class.getSimpleName(), e.getMessage());
//                        }
//                    }
//                });
//                thread.start();

            }
        });

        findViewById(R.id.identify).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UUID[] facesId = new UUID[facesDetected.length];
                for (int i = 0; i < facesDetected.length; i++) {
                    facesId[i] = facesDetected[i].faceId;
                }
                new identificationTask(personGroupId).execute(facesId);
            }
        });
    }

    private void detectAndFrame(final Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());

        AsyncTask<InputStream, String, Face[]> asyncTask = new AsyncTask<InputStream, String, Face[]>() {
            ProgressDialog progressDialog = new ProgressDialog(MainActivity.this);

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                progressDialog.show();
            }

            @Override
            protected void onProgressUpdate(String... values) {
                progressDialog.setMessage(values[0]);
            }

            @Override
            protected Face[] doInBackground(InputStream... inputStreams) {
                publishProgress("Detecting...");
                try {
                    Face[] faces = faceServiceRestClient.detect(inputStreams[0], true, false, null);
                    if (faces == null) {
                        publishProgress("Unable to detect!");
                        progressDialog.dismiss();
                        return null;
                    }
                    publishProgress(String.format("%s face(s) detected!", faces.length));
                    progressDialog.dismiss();
                    return faces;
                } catch (Exception e) {
                    Log.e("Exception", e.getMessage());
                    progressDialog.dismiss();
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Face[] faces) {
                if (faces == null) return;
                facesDetected = new Face[faces.length];
                facesDetected = faces;
                Log.e(MainActivity.class.getSimpleName(), faces.length + " face(s) detected!");
                // imageView.setImageBitmap(drawRectsOnFaces(bitmap, faces));
            }
        };
        asyncTask.execute(byteArrayInputStream);
    }

    private Bitmap drawRectsOnFaces(Bitmap bitmap, Face[] faces) {
        Bitmap localBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(localBitmap);
        Paint paint = new Paint();

        paint.setAntiAlias(true);
        paint.setStrokeWidth(12);
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);

        if (faces != null) {
            for (Face face : faces
                    ) {
                FaceRectangle faceRectangle = face.faceRectangle;
                canvas.drawRect(faceRectangle.left, faceRectangle.top,
                        faceRectangle.left + faceRectangle.width,
                        faceRectangle.height + faceRectangle.top, paint);

            }
        }
        return localBitmap;

    }

    private class identificationTask extends AsyncTask<UUID, String, IdentifyResult[]> {

        String personGroupId;
        private ProgressDialog progressDialog = new ProgressDialog(MainActivity.this);


        public identificationTask(String personGroupId) {
            this.personGroupId = personGroupId;
        }

        @Override
        protected void onPreExecute() {
            progressDialog.show();
        }

        @Override
        protected void onPostExecute(IdentifyResult[] identifyResult) {
            progressDialog.dismiss();
            if (identifyResult.length > 0) {
                for (IdentifyResult result : identifyResult) {
                    Log.e("candidates", String.valueOf(result.candidates.size()));
                    new PersonDetectTask(this.personGroupId).execute(result.candidates.get(0).personId);
                }
            } else
                Log.e(MainActivity.class.getSimpleName(), "IdentifyArray is empty");

        }

        @Override
        protected IdentifyResult[] doInBackground(UUID... uuids) {
            try {
                publishProgress("Getting person group status...");
                TrainingStatus trainingStatus = faceServiceRestClient.getPersonGroupTrainingStatus(this.personGroupId);
                Log.e("trainingStatus", trainingStatus.status + " ");
                progressDialog.dismiss();

                if (trainingStatus.status != TrainingStatus.Status.Succeeded) {
                    publishProgress("Person group training status is " + trainingStatus.status);
                    return null;
                }
                publishProgress("Identifying...");


                return faceServiceRestClient.identity(this.personGroupId, uuids, 1);

            } catch (ClientException e) {
                Log.e("ClientException", e.getMessage());
                return null;
            } catch (IOException e) {
                Log.e("IOException", e.getMessage());
                return null;
            }
        }

        @Override
        protected void onProgressUpdate(String... values) {
            progressDialog.setMessage(values[0]);
        }
    }

    private class PersonDetectTask extends AsyncTask<UUID, String, Person> {

        String personGroupId;
        ProgressDialog dialog = new ProgressDialog(MainActivity.this);

        public PersonDetectTask(String personGroupId) {
            this.personGroupId = personGroupId;

        }


        @Override
        protected void onPreExecute() {
            dialog.show();
        }

        @Override
        protected void onProgressUpdate(String... values) {
            dialog.setMessage(values[0]);
        }

        @Override
        protected void onPostExecute(Person person) {
            imageView.setImageBitmap(drawRectOnImage(bitmap, facesDetected, person.name));
        }

        @Override
        protected Person doInBackground(UUID... uuids) {
            try {
                // publishProgress("Getting person group status...");
                Log.e("id", uuids[0] + "");
                Person person = faceServiceRestClient.getPerson(this.personGroupId, uuids[0]);
                dialog.dismiss();
                Log.e("personname", person.name);
                names = names.concat(person.name + " ");
                return person;
            } catch (ClientException e) {
                Log.e("ClientException", e.getMessage());
                return null;


            } catch (IOException e) {
                Log.e("IOException", e.getMessage());
                return null;
            }
        }
    }

    private Bitmap drawRectOnImage(Bitmap bitmap, Face[] facesDetected, String name) {
        Log.e(MainActivity.class.getSimpleName(), "inside rectangel");
        Bitmap copy = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(copy);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);

        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(12);

        if (facesDetected != null) {
            for (Face face : facesDetected
                    ) {
                FaceRectangle faceRectangle = face.faceRectangle;
                canvas.drawRect(faceRectangle.left, faceRectangle.top,
                        faceRectangle.left + faceRectangle.width,
                        faceRectangle.height + faceRectangle.top, paint);

               // drawNameOnCanvas(canvas, 50, ((faceRectangle.width + faceRectangle.left) / 2) + 100, ((faceRectangle.top + faceRectangle.height) / 2) + 50, Color.WHITE, name);

                textView.setVisibility(View.VISIBLE);
                textView.setText(names);
            }
        }
        return copy;
    }

    private void drawNameOnCanvas(Canvas canvas, int textSize, int x, int y, int color, String name) {

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(12);
        paint.setTextSize(20);

        float textWidth = paint.measureText(name);
        canvas.drawText(name, x - (textWidth / 2), y - (textSize / 2), paint);

    }
}
