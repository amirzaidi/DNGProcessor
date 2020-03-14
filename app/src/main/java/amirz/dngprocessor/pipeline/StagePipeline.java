package amirz.dngprocessor.pipeline;

import android.graphics.Bitmap;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import amirz.dngprocessor.colorspace.ColorspaceConverter;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.ShaderLoader;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.pipeline.convert.GreenDemosaic;
import amirz.dngprocessor.pipeline.convert.PreProcess;
import amirz.dngprocessor.pipeline.convert.ToIntermediate;
import amirz.dngprocessor.pipeline.intermediate.BilateralFilter;
import amirz.dngprocessor.pipeline.intermediate.SampleHistogram;
import amirz.dngprocessor.pipeline.intermediate.MergeDetail;
import amirz.dngprocessor.pipeline.post.ToneMap;

public class StagePipeline implements AutoCloseable {
    private static final String TAG = "StagePipeline";

    private final List<Stage> mStages = new ArrayList<>();

    private final GLCoreBlockProcessing mCore;
    private final GLPrograms mConverter;

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

        ColorspaceConverter colorspace = new ColorspaceConverter(sensor);

        // RAW -> XYZ
        addStage(new PreProcess(sensor, raw));
        addStage(new GreenDemosaic());
        addStage(new ToIntermediate(sensor, colorspace.sensorToXYZ_D50));

        // Intermediates
        addStage(new BilateralFilter(process));
        addStage(new SampleHistogram(outWidth, outHeight,
                sensor.outputOffsetX, sensor.outputOffsetY));
        addStage(new MergeDetail());

        // XYZ -> sRGB
        addStage(new ToneMap(sensor, process, colorspace.XYZtoProPhoto,
                colorspace.proPhotoToSRGB));
    }

    private void addStage(Stage stage) {
        if (stage.isEnabled()) {
            stage.init(mConverter);
            mStages.add(stage);
        }
    }

    public void execute(OnProgressReporter reporter) {
        int stageCount = mStages.size();
        for (int i = 0; i < stageCount; i++) {
            reporter.onProgress(i, stageCount);
            mStages.get(i).execute(new StageMap(mStages.subList(0, i)));
        }

        // Assume that last stage set everything but did not render yet.
        mCore.drawBlocksToOutput();

        reporter.onProgress(stageCount, stageCount);
    }

    @Override
    public void close() {
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
