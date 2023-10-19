public class ReadWriteHandlerFactory implements IReadWriteHandlerFactory {
    public IReadWriteHandler createHandler() {
        return new ReadWriteHandler();
    }
}
