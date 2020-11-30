package com.example.advspotlightsearch;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private Map<String, ArrayList<String>> finalMapper = new HashMap<>();
    private String global_word;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    // This function is for normal search.
    public void normalSearch(View view) {
        EditText searchText = findViewById(R.id.search_textbox);
        String searchString = searchText.getText().toString();

        /*
        String state = Environment.getExternalStorageState();
        ArrayList<String> myList = new ArrayList<>();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            if (Build.VERSION.SDK_INT >= 23) {
                if (checkPermission()) {
                    File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Dataset/");
                    if (dir.exists()) {
                        File list[] = dir.listFiles();
                        for (int i = 0; i < list.length; i++) {
                            myList.add(list[i].getName());
                        }
                    }
                } else {
                    requestPermission(); // Code for permission
                }
            } else {
                File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Dataset/");
                if (dir.exists()) {
                    File list[] = dir.listFiles();
                    for (int i = 0; i < list.length; i++) {
                        myList.add(list[i].getName());
                    }
                }
            }
        }
        */

        // Finding the SD card location
        String state = "/data/data/com.example.advspotlightsearch/Dataset";
        ArrayList<String> myList = new ArrayList<>();
        getAllFilePaths(new File(state), myList);

        long startTime = System.nanoTime();
        ArrayList<String> finalResults = new ArrayList<>();
        for(String fileName : myList) {
            if (fileName.matches("(?i:.*" + searchString + "*)")){
                finalResults.add("\t" + fileName + "\n");
            }
        }
        for (String fileName : myList) {
            finalResults.addAll(searchWordInFile(fileName, searchString));
        }
        long endTime = System.nanoTime();

        TextView resultTextView = findViewById(R.id.search_results);
        resultTextView.setText(TextUtils.join("\n", finalResults));
        resultTextView.setMovementMethod(new ScrollingMovementMethod());

        TextView summaryTextView = findViewById(R.id.result_summary);
        summaryTextView.setMovementMethod(new ScrollingMovementMethod());
        String textViewString = finalResults.size() + " results, in " +
                (endTime - startTime) / 1000000 + " milliseconds";
        summaryTextView.setText(textViewString);
    }

//    This function is for Advanced search.
    public void parallelSearch(View view) {
        EditText searchText = findViewById(R.id.search_textbox);
        String searchString = searchText.getText().toString();
        // Finding the SD card location
        String state = "/data/data/com.example.advspotlightsearch/Dataset";
        ArrayList<String> myList = new ArrayList<>();
        getAllFilePaths(new File(state), myList);
        System.out.println(Runtime.getRuntime().availableProcessors());

        /*
        // Map - key - filename and values
        Map<String, ArrayList<String>> mapper = new HashMap<>();
        myList.parallelStream().forEach(fileName -> {
            mapper.put(fileName, searchWordInFile(fileName, searchString));
        });
        */

        this.global_word = searchString;
        ArrayList<Runnable> all_tasks = new ArrayList<>();
        for(String fileName: myList){
            all_tasks.add(new Task(fileName));
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Collections.sort(myList, (o1, o2) -> (int) (new File(o2).length() - new File(o1).length()));
        ArrayList<String> finalResults = new ArrayList<>();
        for(String fileName : myList) {
            if (fileName.matches("(?i:.*" + searchString + "*)")){
                finalResults.add("\t" + fileName + "\n");
            }
        }
        long startTime = System.nanoTime();
        for(Runnable t: all_tasks){
            pool.execute(t);
        }
        pool.shutdown();
        try {
            pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            System.out.println("Failed while waiting for threads.");
        }
        long endTime = System.nanoTime();

        TextView summaryTextView = findViewById(R.id.result_summary);
        for (Map.Entry<String, ArrayList<String>> entry : this.finalMapper.entrySet()){
            finalResults.addAll(entry.getValue());
        }
        String textViewString = finalResults.size() + " results, in " +
                (endTime - startTime) / 1500000 + " milliseconds";
        summaryTextView.setText(textViewString);

        TextView resultTextView = findViewById(R.id.search_results);
        resultTextView.setText(TextUtils.join("\n", finalResults));
        resultTextView.setMovementMethod(new ScrollingMovementMethod());
    }

    private void getAllFilePaths(File dir, ArrayList<String> allFiles) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                getAllFilePaths(file, allFiles);
            } else {
                allFiles.add(file.getAbsolutePath());
            }
        }
    }

    private ArrayList<String> searchWordInFile(String fileName, String word) {
        try {
            BufferedReader bf = new BufferedReader(new FileReader(fileName));
            String line, temp;
            int lineNo = 0;
            Pattern pt = Pattern.compile(word);
            ArrayList<String> finalResult = new ArrayList<>();
            while ((line = bf.readLine()) != null) {
                lineNo += 1;
                Matcher match = pt.matcher(line);
                while (match.find()) {
                    temp = "Line: " + lineNo + ": " +
                            line.substring(Math.max(0, match.start() - 15),
                                    Math.min(match.start() + 20 + word.length(), line.length()));
                    finalResult.add("\t" + fileName + '\n' + temp + '\n');
                }
            }
            return finalResult;
        } catch (IOException e) {
            System.out.println("IO Error Occurred: " + e.toString());
            return new ArrayList<>();
        }
    }

    class Task implements Runnable {
        private final String fileName;
        public Task(String fileName) {
            this.fileName = fileName;
        }
        public void run() {
            MainActivity.this.finalMapper.put(fileName, MainActivity.this.searchWordInFile(fileName, MainActivity.this.global_word));
        }
    }
}