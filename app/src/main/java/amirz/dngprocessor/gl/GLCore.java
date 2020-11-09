package amirz.dngprocessor.gl;

public abstract class GLCore implements AutoCloseable {
    private final GLPrograms mProgram;

    public GLCore(int surfaceWidth, int surfaceHeight, ShaderLoader loader) {
        GLCoreManager.setDimens(surfaceWidth, surfaceHeight);
        mProgram = new GLPrograms(loader);
    }

    public GLPrograms getProgram() {
        return mProgram;
    }

    @Override
    public void close() {
        mProgram.close();
    }
}
