// License: GPL. For details, see LICENSE file.
package smed;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.Collection;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.SelectionChangedListener;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;

import messages.Messages;
import panels.PanelMain;

public class SmedAction extends JosmAction implements SelectionChangedListener {

    private static final long serialVersionUID = 1L;
    private static String editor = tr("SeaMap Editor");
    public static JFrame editFrame = null;
    private boolean isOpen = false;
    public static PanelMain panelMain = null;

    public OsmPrimitive node = null;
    private Collection<? extends OsmPrimitive> selection = null;

    public SmedAction() {
        super(editor, "Smed", editor, null, true);
    }

    @Override
    public void actionPerformed(ActionEvent arg0) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (!isOpen) {
                    createFrame();
                } else {
                    editFrame.toFront();
                }
                isOpen = true;
            }
        });
    }

    protected void createFrame() {
        editFrame = new JFrame(editor);
        editFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        editFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                closeDialog();
            }
        });
        editFrame.setSize(new Dimension(420, 430));
        editFrame.setLocation(100, 200);
        editFrame.setResizable(true);
        editFrame.setAlwaysOnTop(true);
        editFrame.setVisible(true);
        editFrame.setLayout(null);
        panelMain = new PanelMain(this);
        panelMain.setBounds(10, 10, 400, 400);
        node = null;
        panelMain.syncPanel();
        editFrame.add(panelMain);
        DataSet.addSelectionListener(this);

        // System.out.println("hello");
    }

    public void closeDialog() {
        if (isOpen) {
            editFrame.setVisible(false);
            editFrame.dispose();
        }
        isOpen = false;
    }

    @Override
    public void selectionChanged(Collection<? extends OsmPrimitive> newSelection) {
        OsmPrimitive nextNode = null;
        selection = newSelection;

        for (OsmPrimitive osm : selection) {
            nextNode = osm;
            if (selection.size() == 1) {
                if (nextNode.compareTo(node) != 0) {
                    node = nextNode;
                    panelMain.mark.parseMark(node);
                }
            } else {
                node = null;
                panelMain.mark.clrMark();
                PanelMain.messageBar.setText(Messages.getString("OneNode"));
            }
        }
        if (nextNode == null) {
            node = null;
            panelMain.mark.clrMark();
            PanelMain.messageBar.setText(Messages.getString("SelectNode"));
        }
    }

}
