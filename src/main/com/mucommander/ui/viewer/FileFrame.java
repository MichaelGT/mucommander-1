package com.mucommander.ui.viewer;

import com.mucommander.cache.WindowsStorage;
import com.mucommander.commons.file.AbstractFile;
import com.mucommander.ui.dialog.DialogToolkit;
import com.mucommander.ui.dialog.InformationDialog;
import com.mucommander.ui.helper.FocusRequester;
import com.mucommander.ui.layout.AsyncPanel;
import com.mucommander.ui.macosx.IMacOsWindow;
import com.mucommander.ui.main.MainFrame;
import com.mucommander.ui.quicklist.QuickListContainer;
import org.fife.ui.StatusBar;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Image;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.event.KeyEvent;

/**
 * This class is used as an abstraction for the {@link EditorFrame} and {@link ViewerFrame}.
 *
 * @author Arik Hadas
 */
public abstract class FileFrame extends JFrame implements QuickListContainer, IMacOsWindow {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileFrame.class);

    /**
     * The file presenter within this frame
     */
    private FilePresenter filePresenter;

    /**
     * The main frame from which this frame was initiated
     */
    private MainFrame mainFrame;

    private Component returnFocusTo;

    FileFrame(MainFrame mainFrame, Image icon) {
        this.mainFrame = mainFrame;

        initLookAndFeel();

        setIconImage(icon);

        initLookAndFeel();

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        setResizable(true);

        //FileViewersList.update();
        //initContentPane(file);
    }

    void initContentPane(final AbstractFile file) {
        try {
            filePresenter = createFilePresenter(file);
        } catch (UserCancelledException e) {
            e.printStackTrace();
            // May get a UserCancelledException if the user canceled (refused to confirm the operation after a warning)
            return;
        }
        // If not suitable presenter was found for the given file
        if (filePresenter == null) {
            showGenericErrorDialog();
            return;
        }
        AsyncPanel asyncPanel = createAsyncPanel(file);

        // Add the AsyncPanel to the content pane
        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.add(asyncPanel, BorderLayout.CENTER);

        // Add status bar if exists
        StatusBar statusBar = filePresenter.getStatusBar();
        if (statusBar != null) {
            contentPane.add(statusBar, BorderLayout.SOUTH);
        }

        setContentPane(contentPane);
        //setSize(WAIT_DIALOG_SIZE);
        //setFullScreenSize();
        //setFullScreen(true);
        if (!WindowsStorage.getInstance().init(this, filePresenter.getClass().getCanonicalName(), true)) {
            setSize(800, 600);
            DialogToolkit.centerOnWindow(this, mainFrame);
        }

        setVisible(true);
        FileViewersList.update();
    }

    @NotNull
    private AsyncPanel createAsyncPanel(AbstractFile file) {
        return new AsyncPanel() {
            @Override
            public void initTargetComponent() throws Exception {
                // key dispatcher for Esc detection
                final KeyEventDispatcher keyEventDispatcher = e -> {
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        cancel();
                        setVisible(false);
                        dispose();
                    }
                    return false;
                };
                KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyEventDispatcher);
                try {
                    filePresenter.open(file);
                } finally {
                    KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyEventDispatcher);
                }
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyEventDispatcher);
            }

            @Override
            public JComponent getTargetComponent(Exception e) {
                if (e != null) {
                    LOGGER.debug("Exception caught", e);
                    showGenericErrorDialog();
                    dispose();
                    return filePresenter == null ? new JPanel() : filePresenter;
                }
                setJMenuBar(filePresenter.getMenuBar());
                return filePresenter;
            }


            @Override
            protected void updateLayout() {
                super.updateLayout();
                // Sets panel to preferred size, without exceeding a maximum size and with a minimum size
                //pack();
                WindowsStorage.getInstance().init(FileFrame.this, filePresenter.getClass().getCanonicalName(), true);
                // Request focus on the viewer when it is visible
                FocusRequester.requestFocus(filePresenter);

                // Restore (caret position, scroll position etc.)
                filePresenter.restoreStateOnStartup();
            }
        };
    }

    private void showGenericErrorDialog() {
        InformationDialog.showErrorDialog(mainFrame, getGenericErrorDialogTitle(), getGenericErrorDialogMessage());
    }

    /**
     * Returns whether this frame is set to be displayed in full screen mode
     *
     * @return true if the frame is set to full screen, false otherwise
     */
    private boolean isFullScreen() {
        return (getExtendedState() & Frame.MAXIMIZED_BOTH) != 0;
    }

    ////////////////////////
    // Overridden methods //
    ////////////////////////

    @Override
    public void pack() {
        if (!isFullScreen()) {
            super.pack();
            DialogToolkit.fitToScreen(this);
            DialogToolkit.fitToMinDimension(this, getMinimumSize());
            DialogToolkit.centerOnWindow(this, mainFrame);
        }
    }

    @Override
    public void dispose() {
        try {
            filePresenter.saveStateOnClose();
            WindowsStorage.getInstance().put(this, filePresenter.getClass().getCanonicalName());
        } catch (Throwable ignore) {
        }
        super.dispose();
        try {
            if (returnFocusTo != null) {
                FocusRequester.requestFocus(returnFocusTo);
                if (returnFocusTo instanceof FileFrame) {
                    FocusRequester.requestFocus(((FileFrame) returnFocusTo).filePresenter);
                    //((FileFrame)returnFocusTo).filePresenter
                }
            }
            FileViewersList.update();
        } catch (Throwable ignore) {
        }
    }

    public FileFrame returnFocusTo(Component returnFocusTo) {
        this.returnFocusTo = returnFocusTo;
        return this;
    }

    public Component getReturnFocusTo() {
        return returnFocusTo;
    }

    //////////////////////
    // Abstract methods //
    //////////////////////

    protected abstract String getGenericErrorDialogTitle();

    protected abstract String getGenericErrorDialogMessage();

    protected abstract FilePresenter createFilePresenter(AbstractFile file) throws UserCancelledException;

    public void setSearchedText(String searchedText) {
        filePresenter.setSearchedText(searchedText);
    }

    public void setSearchedBytes(byte[] searchedBytes) {
        filePresenter.setSearchedBytes(searchedBytes);
    }

    public FilePresenter getFilePresenter() {
        return filePresenter;
    }

    @Override
    public void setTitle(String title) {
        super.setTitle(title);
        FileViewersList.update();
    }

    public MainFrame getMainFrame() {
        return mainFrame;
    }

    @Override
    public Point calcQuickListPosition(Dimension dim) {
        int x = Math.max((getWidth() - (int) dim.getWidth()) / 2, 0);
        int y = Math.max((getHeight() - (int) dim.getHeight()) / 2, 0);
        return new Point(x, y);
    }

    @Override
    public Component containerComponent() {
        return this;
    }

    @Override
    public Component nextFocusableComponent() {
        return this;
    }

}
