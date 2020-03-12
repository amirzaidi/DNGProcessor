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
import amirz.dngprocessor.pipeline.intermediate.BilateralFilter;
import amirz.dngprocessor.pipeline.intermediate.SampleHistogram;
import amirz.dngprocessor.pipeline.intermediate.SplitDetail;
import amirz.dngprocessor.pipeline.post.ToneMap;

public class StagePipeline implements AutoCloseable {
    private final List<Stage> mStages = new ArrayList<>();
    private final GLControllerRawConverter mController;
    private final GLProgramRawConverter mConverter;
    private final GLTexPool mTexPool;

    public StagePipeline(SensorParams sensor, ProcessParams process,
                         byte[] raw, Bitmap argbOutput, ShaderLoader loader) {
        mController = new GLControllerRawConverter(sensor, process, raw, argbOutput, loader);

        mConverter = mController.getProgram();
        mTexPool = mConverter.getTexPool();

        addStage(new PreProcess());
        addStage(new GreenDemosaic());
        addStage(new ToIntermediate());

        addStage(new SampleHistogram());
        addStage(new BilateralFilter());
        addStage(new SplitDetail());

        addStage(new ToneMap());
    }

    private void addStage(Stage stage) {
        if (stage.isEnabled()) {
            stage.init(mConverter, mTexPool);
            mStages.add(stage);
        }
    }

    public void execute(OnProgressReporter reporter) {
        mController.sensorToIntermediate();
        mController.analyzeIntermediate();
        mController.blurIntermediate();
        mController.intermediateToOutput();

        int stageCount = mStages.size();
        for (int i = 0; i < stageCount; i++) {
            reporter.onProgress(i, stageCount);
            mStages.get(i).execute(mStages.subList(0, i));
        }
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
