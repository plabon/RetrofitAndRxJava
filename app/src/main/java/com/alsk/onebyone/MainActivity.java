package com.alsk.onebyone;

import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;

import com.alsk.onebyone.databinding.ActivityMainBinding;
import com.alsk.onebyone.hugejsonservice.models.Feature;
import com.alsk.onebyone.hugejsonservice.rest.HugeJsonApi;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.concurrent.Callable;

import io.reactivex.Emitter;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.BiFunction;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;
import okhttp3.ResponseBody;

public class MainActivity extends AppCompatActivity {

    public static final int TOTAL_ELEMENTS_COUNT = 206560;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        playHugeJsonSample();
    }

    public void playHugeJsonSample() {

        HugeJsonApi hugeJsonApi = RestUtils.createService(HugeJsonApi.class, HugeJsonApi.SERVICE_ENDPOINT);

        final int[] counter = {0};
        Gson gson = new GsonBuilder().create();

        hugeJsonApi.get()
                .flatMap(responseBody -> convertObjectsStream(responseBody, gson, Feature.class))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<Feature>() {

                    @Override
                    public void onSubscribe(Subscription s) {

                        binding.progressbar.setMax(TOTAL_ELEMENTS_COUNT);
                        binding.progressbar.setIndeterminate(false);
                        binding.progressbar.setProgress(0);
                        request(1);
                    }

                    @Override
                    public void onNext(Feature feature) {
                        counter[0]++;
                        binding.tvCounter.setText("Read elements counter: " + counter[0]);
                        binding.tvLastElement.setText("Last read element: " + gson.toJson(feature));
                        binding.tvStatus.setText("Used memory: " + getUsedMemoryInMb() + "Mb");
                        binding.progressbar.setProgress(counter[0]);
                        request(1);
                    }

                    @Override
                    public void onError(Throwable e) {
                        binding.tvStatus.setText("Status: something went wrong " + e.getMessage());
                    }

                    @Override
                    public void onComplete() {
                        binding.tvStatus.setText("Status: successfully completed");
                    }
                });
    }

    private long getUsedMemoryInMb() {
        final Runtime runtime = Runtime.getRuntime();
        final long usedMemInMB = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
        //final long maxHeapSizeInMB=runtime.maxMemory() / 1048576L;
        return usedMemInMB;
    }

    @NonNull
    private static <TYPE> Observable<TYPE> convertObjectsStream(ResponseBody responseBody, Gson gson, Class<TYPE> clazz) {
        Type type = TypeToken.get(clazz).getType();
        return Observable.generate(new Callable<JsonReader>() {

            @Override
            public JsonReader call() throws Exception {
                try {
                    JsonReader reader = gson.newJsonReader(responseBody.charStream());
                    reader.beginObject();
                    // looking for a "features" field with actual array of elements
                    while (reader.hasNext()) {
                        // the array begins at json-field "features"
                        if (reader.nextName().equals("features")) {
                            reader.beginArray();
                            return reader;
                        }
                        reader.skipValue();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    RxJavaPlugins.onError(e);
                }
                return null;
            }
        }, new BiFunction<JsonReader, Emitter<TYPE>, JsonReader>() {

            @Override
            public JsonReader apply(@NonNull JsonReader jsonReader, @NonNull Emitter<TYPE> typeEmitter) throws Exception {
                        if (jsonReader == null) {
                            typeEmitter.onComplete();
                            return null;
                        }

                        try {
                            if (jsonReader.hasNext()) {
                                TYPE t = gson.fromJson(jsonReader, clazz);
                                typeEmitter.onNext(t);
                            } else {
                                typeEmitter.onComplete();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            typeEmitter.onError(e);
                        }
                        return jsonReader;

                    }
            }

        );


    }
}
