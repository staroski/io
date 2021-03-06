package br.com.staroski.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * This class allows a file to be splitted in parts.<br>
 * It is possible to instantiate a {@link FileSplitter} with the following constructors:
 * <ul>
 * <li>{@link #FileSplitter(File)} providing a physical file;</li>
 * <li>{@link #FileSplitter(String)} providing the path of a physical file;</li>
 * <li>{@link #FileSplitter(FileSplitterModel)} providing an {@link FileSplitterModel} for custom behaviour.</li>
 * </ul>
 * 
 * @author Ricardo Artur Staroski
 */
public final class FileSplitter {

    /**
     * Default implementation of {@link FileSplitterModel} used when calling the {@link FileSplitter#FileSplitter(File) FileSplitter(File)} constructor.
     * 
     * @param file
     *            The input file.
     */
    private static final class DefaultSplitterModel extends AbstractFileSplitterModel {

        private BufferedReader reader;

        DefaultSplitterModel(File file) {
            super(file);
        }

        @Override
        public String readLine(BufferedReader reader) throws IOException {
            return reader.readLine();
        }

        @Override
        public BufferedReader startReading() throws IOException {
            stopReading(reader);
            reader = new BufferedReader(new FileReader(getFile()));
            return reader;
        }

        @Override
        public void stopReading(BufferedReader reader) throws IOException {
            if (reader != null) {
                reader.close();
            }
        }
    }

    /**
     * Used internally to prevent <t>null</t> parameters.
     * 
     * @param parameter
     *            The parameter to be validated.
     * 
     * @param message
     *            The exception message when parameter is null.
     * 
     * @return The parameter itself.
     * 
     * @throws IllegalArgumentException
     *             if parameter is <t>null</t>.
     */
    private static <T> T avoidNull(T parameter, String message) {
        if (parameter == null) {
            throw new IllegalArgumentException(message);
        }
        return parameter;
    }

    // the model used by this file splitter
    private final FileSplitterModel model;

    // the output folder
    private File outputFolder;

    /**
     * Creates an {@link FileSplitter} for the given {@link File} object.
     * 
     * @param file
     *            File to be splitted.
     */
    public FileSplitter(final File file) {
        this(new DefaultSplitterModel(avoidNull(file, "The 'file' parameter cannot be null")));
    }

    /**
     * Creates an {@link FileSplitter} for the given {@link FileSplitterModel} object.
     * 
     * @param model
     *            The model that provides the file to be splitted.
     */
    public FileSplitter(FileSplitterModel model) {
        this.model = avoidNull(model, "The 'model' parameter cannot be null");
    }

    /**
     * Creates an {@link FileSplitter} for the file located on the given path.
     * 
     * @param path
     *            Path of file to be splitted.
     */
    public FileSplitter(String path) {
        this(new File(avoidNull(path, "The 'path' parameter cannot be null")));
    }

    /**
     * Returns the output folder into where the parts will be generated.<br>
     * By default it returns a {@link File} corresponding to <code>System.getProperty("user.dir")</code>.<br>
     * Can be changed through the {@link #setOutputFolder(File)} method.
     * 
     * @return The output folder into where the parts will be generated.
     * 
     * @see #setOutputFolder(File)
     */
    public File getOutputFolder() {
        if (outputFolder == null) {
            outputFolder = new File(System.getProperty("user.dir"));
        }
        return outputFolder;
    }

    /**
     * Sets the output folder into where the parts will be generated.<br>
     * Affect the result of {@link #getOutputFolder()} method.
     * 
     * @param directory
     *            The output folder.
     * 
     * @return This object itself, allowing enchained calls.
     * 
     * @see #getOutputFolder()
     */
    public FileSplitter setOutputFolder(File directory) {
        if (directory != null && directory.exists() && !directory.isDirectory()) {
            throw new IllegalArgumentException("\"" + directory.getAbsolutePath() + "\" is not a directory");
        }
        this.outputFolder = directory;
        return this;
    }

    /**
     * Sets the output folder into where the parts will be generated.<br>
     * Affect the result of {@link #getOutputFolder()} method.
     * 
     * @param path
     *            The path of the output folder.
     * 
     * @return This object itself, allowing enchained calls.
     * 
     * @see #getOutputFolder()
     */
    public FileSplitter setOutputFolder(String path) {
        return setOutputFolder(path == null ? (File) null : new File(path));
    }

    /**
     * Splits the enclosed file into the specified number of parts.<br>
     * Writes the parts into the {@link #getOutputFolder() output folder}.<br>
     * Returns the {@link File} objects created for each part.
     * 
     * @param parts
     *            The number of parts that the enclosed file will be splitted.
     * 
     * @return An array of {@link File} objects for each part.
     * 
     * @see #setOutputFolder(File)
     */
    public File[] split(int parts) throws Exception {
        final String modelClassName = model.getClass().getName();
        final String startReadingNull = "Method " + modelClassName + ".startReading() returned null";
        final String startWritingNull = "Method " + modelClassName + ".startWriting(File) returned null";
        int lines; // start counting the amount of lines of the input file
        BufferedReader reader = avoidNull(model.startReading(), startReadingNull); // open the input file reader
        for (lines = 0; model.readLine(reader) != null; lines++) { /* read whole file to count lines */ }
        model.stopReading(reader); // close the input file reader
        model.initialize(lines, parts); // notify that the lines were read and it's ready to split in parts
        reader = avoidNull(model.startReading(), startReadingNull); // open the input file reader
        File[] partFiles = prepareParts(parts); // prepare the File objects for each part
        int currentPart = 0; // index of the current part file writer
        int line = 0; // reset the line counter
        PrintWriter writer = avoidNull(model.startWriting(partFiles[currentPart]), startWritingNull); // notify start writing the part file
        String content = null; // read the content of the input file
        while ((content = model.readLine(reader)) != null) {
            line++; // increment line number
            writer.println(content); // copy the content from the input file to the current part file
            if (model.canSplit(line, content)) { // check if can close the current part and open the next
                writer.flush(); // flush the current part file writer prior to notify stop writing
                model.stopWriting(partFiles[currentPart], writer); // notify stop writing on the part file
                writer.flush(); // ensure the the current part file writer is flushed after notify stop writing
                writer.close(); // close the current part file writer
                currentPart++; // index of the next part file writer
                line = 0; // reset the line counter
                writer = avoidNull(model.startWriting(partFiles[currentPart]), startWritingNull); // notify start writing the part file
            }
        }
        model.stopReading(reader); // close the input file reader
        writer.flush(); // flush the current part file writer prior to notify stop writing
        model.stopWriting(partFiles[currentPart], writer); // notify stop writing on the part file
        writer.flush(); // ensure the the current part file writer is flushed after notify stop writing
        writer.close(); // close the current part file writer
        return onlyExisting(partFiles); // return the part files to the caller
    }

    /**
     * Given some {@link File} objects, returns only the ones that actually exist on file system.
     * 
     * @param files
     *            The array of {@link File} objects.
     * 
     * @return An array containing only the {@link File} objects that actually exist on file system.
     */
    private File[] onlyExisting(File[] files) {
        List<File> existing = new ArrayList<>();
        for (File file : files) {
            if (file.exists()) {
                existing.add(file);
            }
        }
        return existing.toArray(new File[existing.size()]);
    }

    /**
     * Creates an {@link File} array for the specified amount of parts.
     * 
     * @param parts
     *            The number of parts.
     * 
     * @return An {@link File} array.
     */
    private File[] prepareParts(int parts) {
        File[] partFiles = new File[parts];
        File folder = getOutputFolder();
        folder.mkdirs(); // ensure that the output directory tree exists
        for (int number = 0; number < parts; number++) {
            partFiles[number] = new File(folder, model.getPartName(number));
        }
        return partFiles;
    }
}