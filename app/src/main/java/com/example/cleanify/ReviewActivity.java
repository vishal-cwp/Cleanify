package com.example.cleanify;

import android.app.Activity;
import android.os.Bundle;
import android.Manifest;
import android.widget.Toast;
import android.content.pm.PackageManager;
import android.os.Build;

import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;

import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import android.util.Size;
import java.io.IOException;
import android.graphics.Bitmap;

import android.app.DatePickerDialog;
import android.widget.Button;

import java.util.Calendar;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class ReviewActivity extends Activity {

    private static final int MEDIA_PERMISSION_REQUEST = 100;
    private ArrayList<Uri> mediaUris = new ArrayList<>();
    private ArrayList<Integer> mediaTypes = new ArrayList<>();
    private SharedPreferences reviewPrefs;

    private ImageView mediaImage;
    private TextView mediaCounter;
    private TextView mediaTypeLabel;

    private Button fromDateButton;
    private Button toDateButton;
    private Button applyDateFilterButton;

    private Calendar fromDate;
    private Calendar toDate;

    private int currentIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review);

        fromDateButton = findViewById(R.id.fromDateButton);
        toDateButton = findViewById(R.id.toDateButton);
        applyDateFilterButton = findViewById(R.id.applyDateFilterButton);

        fromDate = null;
        toDate = null;

        fromDateButton.setOnClickListener(v -> showDatePicker(true));
        toDateButton.setOnClickListener(v -> showDatePicker(false));

        applyDateFilterButton.setOnClickListener(v -> {
            if (fromDate == null || toDate == null) {
                Toast.makeText(
                        this,
                        "Please select both dates.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            if (fromDate.after(toDate)) {
                Toast.makeText(
                        this,
                        "The start date must not be after the end date.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            currentIndex = 0;
            loadMedia();
        });

        mediaImage = findViewById(R.id.mediaImage);
        mediaCounter = findViewById(R.id.mediaCounter);
        mediaTypeLabel = findViewById(R.id.mediaTypeLabel);
        setupSwipeGestures();
        reviewPrefs = getSharedPreferences(
                "cleanify_reviews",
                MODE_PRIVATE);

        if (hasFullMediaAccess()) {
            loadMedia();
        } else {
            requestMediaPermissions();
        }

    }

    private void showDatePicker(boolean selectingFromDate) {
        Calendar initialDate = Calendar.getInstance();

        Calendar selectedDate = selectingFromDate
                ? fromDate
                : toDate;

        if (selectedDate != null) {
            initialDate.setTimeInMillis(selectedDate.getTimeInMillis());
        }

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar chosen = Calendar.getInstance();

                    chosen.clear();
                    chosen.set(year, month, dayOfMonth, 0, 0, 0);
                    chosen.set(Calendar.MILLISECOND, 0);

                    if (selectingFromDate) {
                        fromDate = chosen;
                        fromDateButton.setText(
                                "From: " + formatDate(chosen));
                    } else {
                        toDate = chosen;
                        toDateButton.setText(
                                "To: " + formatDate(chosen));
                    }
                },
                initialDate.get(Calendar.YEAR),
                initialDate.get(Calendar.MONTH),
                initialDate.get(Calendar.DAY_OF_MONTH));

        dialog.show();
    }

    private String formatDate(Calendar date) {
        return new SimpleDateFormat(
                "dd MMM yyyy",
                Locale.getDefault()).format(date.getTime());
    }

    private void setupSwipeGestures() {
        GestureDetector gestureDetector = new GestureDetector(
                this,
                new GestureDetector.SimpleOnGestureListener() {

                    private static final int SWIPE_THRESHOLD = 100;
                    private static final int SWIPE_VELOCITY_THRESHOLD = 100;

                    @Override
                    public boolean onDown(MotionEvent event) {
                        return true;
                    }

                    @Override
                    public boolean onFling(
                            MotionEvent startEvent,
                            MotionEvent endEvent,
                            float velocityX,
                            float velocityY) {

                        float distanceX = endEvent.getX() - startEvent.getX();
                        float distanceY = endEvent.getY() - startEvent.getY();

                        if (Math.abs(distanceX) > Math.abs(distanceY)
                                && Math.abs(distanceX) > SWIPE_THRESHOLD
                                && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {

                            if (distanceX > 0) {
                                // Swipe right: Keep
                                reviewCurrentMedia(false);
                            } else {
                                // Swipe left: Mark for deletion
                                reviewCurrentMedia(true);
                            }

                            return true;
                        }

                        return false;
                    }
                });

        mediaImage.setOnTouchListener((view, event) -> gestureDetector.onTouchEvent(event));
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);

        if (requestCode == MEDIA_PERMISSION_REQUEST) {
            if (hasFullMediaAccess()) {
                loadMedia();
            } else {
                Toast.makeText(
                        this,
                        "Full access to photos and videos is required.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void reviewCurrentMedia(boolean markedForDeletion) {
        if (currentIndex >= mediaUris.size()) {
            return;
        }

        Uri currentUri = mediaUris.get(currentIndex);

        recordReview(currentUri, markedForDeletion);

        currentIndex++;
        displayMedia();
    }

    private void displayMedia() {
        if (currentIndex >= mediaUris.size()) {
            mediaImage.setImageDrawable(null);
            mediaCounter.setText("You've reviewed all your media!");
            return;
        }

        Uri mediaUri = mediaUris.get(currentIndex);

        try {
            int mediaType = mediaTypes.get(currentIndex);

            if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                mediaTypeLabel.setText("VIDEO");
            } else {
                mediaTypeLabel.setText("PHOTO");
            }

            if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        Bitmap thumbnail = getContentResolver().loadThumbnail(
                                mediaUri,
                                new Size(1024, 1024),
                                null);

                        mediaImage.setImageBitmap(thumbnail);

                    } catch (IOException e) {
                        mediaImage.setImageDrawable(null);
                    }
                } else {
                    mediaImage.setImageDrawable(null);
                }
            } else {
                mediaImage.setImageURI(mediaUri);
            }

            mediaCounter.setText(
                    (currentIndex + 1) + " / " + mediaUris.size());
        } catch (Exception e) {
            mediaImage.setImageDrawable(null);
            mediaCounter.setText("Unable to display this media");
        }
    }

    private void loadMedia() {
        mediaUris.clear();
        mediaTypes.clear();

        Uri collection = MediaStore.Files.getContentUri("external");

        String[] projection = {
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE
        };

        String selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "=? OR " +
                MediaStore.Files.FileColumns.MEDIA_TYPE + "=?";

        ArrayList<String> selectionArgs = new ArrayList<>();

        selectionArgs.add(String.valueOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE));
        selectionArgs.add(String.valueOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO));

        if (fromDate != null && toDate != null) {
            selection = "(" + selection + ") AND " +
                    MediaStore.MediaColumns.DATE_ADDED + ">=? AND " +
                    MediaStore.MediaColumns.DATE_ADDED + "<?";

            selectionArgs.add(String.valueOf(
                    fromDate.getTimeInMillis() / 1000));

            Calendar dayAfterToDate = (Calendar) toDate.clone();
            dayAfterToDate.add(Calendar.DAY_OF_MONTH, 1);

            selectionArgs.add(String.valueOf(
                    dayAfterToDate.getTimeInMillis() / 1000));
        }

        String sortOrder = MediaStore.MediaColumns.DATE_ADDED + " ASC";

        try (Cursor cursor = getContentResolver().query(
                collection,
                projection,
                selection,
                selectionArgs.toArray(new String[0]),
                sortOrder)) {

            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(
                        MediaStore.Files.FileColumns._ID);
                int typeColumn = cursor.getColumnIndexOrThrow(
                        MediaStore.Files.FileColumns.MEDIA_TYPE);

                Set<String> reviewedUris = reviewPrefs.getStringSet(
                        "reviewed_media",
                        new HashSet<>());

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    int type = cursor.getInt(typeColumn);

                    Uri mediaUri = ContentUris.withAppendedId(collection, id);

                    if (!reviewedUris.contains(mediaUri.toString())) {
                        mediaUris.add(mediaUri);
                        mediaTypes.add(type);
                    }
                }
            }
        }

        displayMedia();

        Toast.makeText(
                this,
                "Found " + mediaUris.size() + " media items",
                Toast.LENGTH_LONG).show();
    }

    private void recordReview(Uri mediaUri, boolean markedForDeletion) {
        Set<String> reviewedUris = new HashSet<>(
                reviewPrefs.getStringSet(
                        "reviewed_media",
                        new HashSet<>()));

        Set<String> keptUris = new HashSet<>(
                reviewPrefs.getStringSet(
                        "kept_media",
                        new HashSet<>()));

        Set<String> deletionUris = new HashSet<>(
                reviewPrefs.getStringSet(
                        "deletion_media",
                        new HashSet<>()));

        String uri = mediaUri.toString();

        reviewedUris.add(uri);

        if (markedForDeletion) {
            deletionUris.add(uri);
            keptUris.remove(uri);
        } else {
            keptUris.add(uri);
            deletionUris.remove(uri);
        }

        reviewPrefs.edit()
                .putStringSet("reviewed_media", reviewedUris)
                .putStringSet("kept_media", keptUris)
                .putStringSet("deletion_media", deletionUris)
                .apply();
    }

    private void requestMediaPermissions() {
        String[] permissions;
        if (!hasFullMediaAccess()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                permissions = new String[] {
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                };
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions = new String[] {
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO
                };
            } else {
                permissions = new String[] {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                };
            }
            requestPermissions(permissions, MEDIA_PERMISSION_REQUEST);
        }
    }

    private boolean hasFullMediaAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return canReadImages() && canReadVideos();
    }

    private boolean canReadImages() {
        return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean canReadVideos() {
        return checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
    }
}
