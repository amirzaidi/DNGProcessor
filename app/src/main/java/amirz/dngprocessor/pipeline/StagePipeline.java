package amirz.dngprocessor.pipeline;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;

import amirz.dngprocessor.gl.GLTexPool;
import amirz.dngprocessor.gl.ShaderLoader;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.pipeline.convert.GreenDemosaic;
import amirz.dngprocessor.pipeline.convert.PreProcess;
import amirz.dngprocessor.pipeline.convert.ToIntermediate;

public class StagePipeline implements AutoCloseable {
    private final List<Stage> mStages = new ArrayList<>();
    private final GLControllerRawConverter mController;
    private final GLProgramRawConverter mConverter;
    private final GLTexPool mTexPool;
    private final ShaderLoader mShaderLoader;

    public StagePipeline(SensorParams sensor, ProcessParams process,
                         byte[] raw, Bitmap argbOutput, ShaderLoader loader) {
        mController = new GLControllerRawConverter(sensor, process, raw, argbOutput, loader);

        mConverter = mController.getProgram();
        mTexPool = mConverter.getTexPool();
        mShaderLoader = loader;

        addStage(new PreProcess(sensor, raw));
        addStage(new GreenDemosaic());
        addStage(new ToIntermediate(sensor, mController.sensorToXYZ_D50));

        /*
        addStage(new SampleHistogram());
        addStage(new BilateralFilter());
        addStage(new SplitDetail());

        addStage(new ToneMap());
         */
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
            mStages.get(i).execute(mStages.subList(0, i));
        }

        mController.analyzeIntermediate();
        mController.blurIntermediate();
        mController.intermediateToOutput();

        reporter.onProgress(stageCount, stageCount);
    }

    @Override
    public void close() {
        mController.close();
    }

    public interface OnProgressReporter {
        void onProgress(int completed, int total);
    }
}
