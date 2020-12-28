package amirz.dngprocessor.pipeline;

import android.graphics.Bitmap;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import amirz.dngprocessor.colorspace.ColorspaceConverter;
import amirz.dngprocessor.gl.GLCore;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.ShaderLoader;
import amirz.dngprocessor.gl.TexturePool;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.pipeline.convert.EdgeMirror;
import amirz.dngprocessor.pipeline.convert.GreenDemosaic;
import amirz.dngprocessor.pipeline.convert.PreProcess;
import amirz.dngprocessor.pipeline.convert.ToIntermediate;
import amirz.dngprocessor.pipeline.exposefuse.Laplace;
import amirz.dngprocessor.pipeline.exposefuse.Merge;
import amirz.dngprocessor.pipeline.exposefuse.DoubleExpose;
import amirz.dngprocessor.pipeline.intermediate.BilateralFilter;
import amirz.dngprocessor.pipeline.intermediate.Analysis;
import amirz.dngprocessor.pipeline.intermediate.MergeDetail;
import amirz.dngprocessor.pipeline.post.BlurLCE;
import amirz.dngprocessor.pipeline.post.ToneMap;

import static amirz.dngprocessor.util.Constants.BLOCK_HEIGHT;

public class StagePipeline implements AutoCloseable {
    private static final String TAG = "StagePipeline";

    private final List<Stage> mStages = new ArrayList<>();

    private final GLPrograms mConverter;
    private final GLBlockProcessing mBlockProcessing;

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

        GLCore.getInstance().setDimens(argbOutput.getWidth(), BLOCK_HEIGHT);
        mConverter = GLPrograms.getInstance(loader);
        mBlockProcessing = new GLBlockProcessing(argbOutput);

        ColorspaceConverter colorspace = new ColorspaceConverter(sensor);

        // RAW -> XYZ -> xyY
        addStage(new PreProcess(sensor, raw));
        addStage(new GreenDemosaic());
        addStage(new ToIntermediate(sensor, colorspace.sensorToXYZ_D50));
        addStage(new EdgeMirror(sensor));

        // Noise Reduce
        //addStage(new Decompose());
        //addStage(new NoiseMap());
        //addStage(new NoiseReduce(sensor, process));

        // Exposure Fusion
        addStage(new DoubleExpose());
        addStage(new Laplace());
        addStage(new Merge());

        // Contrast Enhancement
        addStage(new Analysis(outWidth, outHeight,
                sensor.outputOffsetX, sensor.outputOffsetY));
        addStage(new BilateralFilter(process));
        addStage(new MergeDetail(process));

        // xyY -> XYZ -> sRGB
        addStage(new BlurLCE(sensor, process));
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
            Stage stage = mStages.get(i);
            reporter.onProgress(i, stageCount, stage.getClass().getSimpleName());
            mConverter.useProgram(stage.getShader());
            stage.execute(new StageMap(mStages.subList(0, i)));
        }

        // Assume that last stage set everything but did not render yet.
        mBlockProcessing.drawBlocksToOutput(mConverter);

        reporter.onProgress(stageCount, stageCount, "Done");
    }

    @Override
    public void close() {
        for (Stage stage : mStages) {
            stage.close();
        }
        mStages.clear();
        TexturePool.logLeaks();

        //mConverter.close();
        //GLCore.closeContext();
    }

    public interface OnProgressReporter {
        void onProgress(int completed, int total, String tag);
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
