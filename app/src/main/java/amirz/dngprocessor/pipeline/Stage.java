package amirz.dngprocessor.pipeline;

import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.params.SensorParams;

public abstract class Stage implements AutoCloseable {
    private GLPrograms mConverter;
    private SensorParams mSensor;
    private ProcessParams mProcess;

    public void init(GLPrograms converter, SensorParams sensor, ProcessParams process) {
        mConverter = converter;
        mSensor = sensor;
        mProcess = process;
    }

    protected GLPrograms getConverter() {
        return mConverter;
    }

    protected SensorParams getSensorParams() {
        return mSensor;
    }

    protected ProcessParams getProcessParams() {
        return mProcess;
    }

    protected boolean isEnabled() {
        return true;
    }

    protected abstract void execute(StagePipeline.StageMap previousStages);

    public abstract int getShader();

    @Override
    public void close() {
    }
}
