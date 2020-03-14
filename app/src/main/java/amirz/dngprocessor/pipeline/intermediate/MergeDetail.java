package amirz.dngprocessor.pipeline.intermediate;

import java.nio.FloatBuffer;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.pipeline.convert.PreProcess;
import amirz.dngprocessor.pipeline.convert.ToIntermediate;

import static android.opengl.GLES20.*;

public class MergeDetail extends Stage {
    private Texture mIntermediate;

    public Texture getIntermediate() {
        return mIntermediate;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        super.execute(previousStages);
        GLPrograms converter = getConverter();

        BilateralFilter bilateral = previousStages.getStage(BilateralFilter.class);
        Texture bilateralTex = bilateral.getBilateral();

        ToIntermediate intermediate = previousStages.getStage(ToIntermediate.class);
        Texture intermediateTex = intermediate.getIntermediate();

        // If there is no bilateral filtered texture, skip this step.
        if (bilateralTex == null) {
            mIntermediate = intermediateTex;
            return;
        }

        PreProcess preProcess = previousStages.getStage(PreProcess.class);
        int w = preProcess.getInWidth();
        int h = preProcess.getInHeight();

        SampleHistogram sampleHistogram = previousStages.getStage(SampleHistogram.class);
        float[] hist = sampleHistogram.getHist();

        Texture histTex = new Texture(hist.length, 1, 1, Texture.Format.Float16,
                FloatBuffer.wrap(hist), GL_LINEAR, GL_CLAMP_TO_EDGE);
        histTex.bind(GL_TEXTURE6);
        converter.seti("hist", 6);

        intermediateTex.bind(GL_TEXTURE0);
        bilateralTex.bind(GL_TEXTURE2);
        converter.seti("intermediate", 0);
        converter.seti("bilateral", 2);
        converter.setf("boost", 1f, 1f);
        mIntermediate = new Texture(w, h, 3, Texture.Format.Float16, null);
        mIntermediate.setFrameBuffer();
        converter.drawBlocks(w, h);

        intermediateTex.close();
        bilateralTex.close();
    }

    @Override
    public int getShader() {
        return R.raw.stage2_3_detail;
    }
}
