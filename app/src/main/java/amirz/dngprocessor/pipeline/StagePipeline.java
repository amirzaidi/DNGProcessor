package amirz.dngprocessor.pipeline;

import android.graphics.Bitmap;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import amirz.dngprocessor.colorspace.ColorspaceConverter;
import amirz.dngprocessor.gl.GLProgramBase;
import amirz.dngprocessor.gl.GLTexPool;
import amirz.dngprocessor.gl.ShaderLoader;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.pipeline.convert.GreenDemosaic;
import amirz.dngprocessor.pipeline.convert.PreProcess;
import amirz.dngprocessor.pipeline.convert.ToIntermediate;
import amirz.dngprocessor.pipeline.intermediate.BilateralFilter;
import amirz.dngprocessor.pipeline.intermediate.SampleHistogram;
import amirz.dngprocessor.pipeline.intermediate.SplitDetail;
import amirz.dngprocessor.pipeline.post.ToneMap;

public class StagePipeline implements AutoCloseable {
    private static final String TAG = "StagePipeline";

    private final List<Stage> mStages = new ArrayList<>();

    private final GLCoreBlockProcessing mCore;
    private final GLProgramBase mConverter;

    private final GLTexPool mTexPool;
    private final ShaderLoader mShaderLoader;

    public StagePipeline(SensorParams sensor, ProcessParams process,
                         byte[] raw, Bitmap argbOutput, ShaderLoader loader) {
        int outWidth = argbOutput.getWidth();
        int outHeight = argbOutput.getHeight();

        if (outWidth + sensor.outputOffsetX > sensor.inputWidth
                || outHeight + sensor.outputOffsetY > sensor.inputHeight) {
            throw new IllegalArgumentException("Raw image with dimensions (w=" + sensor.inputWidth
                    + ", h=" + sensor.inputHeight
                    + "), cannot converted into sRGB image with dimensions (w="
                    + outWidth + ", h=" + outHeight + ").");
        }
        Log.d(TAG, "Output width,height: " + outWidth + "," + outHeight);

        mCore = new GLCoreBlockProcessing(argbOutput, loader);
        mConverter = mCore.getProgram();

        mTexPool = new GLTexPool();
        mShaderLoader = loader;

        ColorspaceConverter colorspace = new ColorspaceConverter(sensor);

        // RAW -> XYZ
        addStage(new PreProcess(sensor, raw));
        addStage(new GreenDemosaic());
        addStage(new ToIntermediate(sensor, colorspace.sensorToXYZ_D50));

        // Intermediaries
        addStage(new SampleHistogram(outWidth, outHeight,
                sensor.outputOffsetX, sensor.outputOffsetY));
        addStage(new BilateralFilter(process));
        addStage(new SplitDetail());

        // XYZ -> sRGB
        addStage(new ToneMap(sensor, process, colorspace.XYZtoProPhoto,
                colorspace.proPhotoToSRGB));
    }

    private void addStage(Stage stage) {
        if (stage.isEnabled()) {
            stage.init(mConverter, mTexPool, mShaderLoader);
            mStages.add(stage);
        }
    }

    public void execute(OnProgressReporter reporter) {
        int stageCount = mStages.size();
        for (int i = 0; i < stageCount; i++) {
            reporter.onProgress(i, stageCount);
            mStages.get(i).execute(new StageMap(mStages.subList(0, i)));
        }

        // Replacement for ToneMap calling it.
        mCore.intermediateToOutput();

        reporter.onProgress(stageCount, stageCount);
    }

    @Override
    public void close() {
        mTexPool.close();
        mCore.close();
    }

    public interface OnProgressReporter {
        void onProgress(int completed, int total);
    }

    public static class StageMap {
        private final List<Stage> mStages;

        private StageMap(List<Stage> stages) {
            mStages = stages;
        }

        @SuppressWarnings("unchecked")
        public <T> T getStage(Class<T> cls) {
            for (Stage stage : mStages) {
                if (stage.getClass() == cls) {
                    return (T) stage;
                }
            }
            return null;
        }
    }
}
