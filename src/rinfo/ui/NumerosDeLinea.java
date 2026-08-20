package rinfo.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

/**
 * Regla de números de línea para el editor.
 *
 * <p>Va como cabecera de fila del scroll: se dibuja alineada con las líneas
 * del texto usando las mismas métricas de fuente que el editor.
 */
public final class NumerosDeLinea extends JComponent {

    private static final Color FONDO = new Color(0xF0F0EC);
    private static final Color BORDE = new Color(0xD5D8D2);
    private static final Color TEXTO = new Color(0x98A098);

    private final JTextComponent editor;

    public NumerosDeLinea(JTextComponent editor) {
        this.editor = editor;
        setFont(editor.getFont());
        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                actualizar();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                actualizar();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                actualizar();
            }
        });
    }

    private void actualizar() {
        SwingUtilities.invokeLater(() -> {
            revalidate();
            repaint();
        });
    }

    private int cantidadDeLineas() {
        return editor.getDocument().getDefaultRootElement().getElementCount();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(editor.getFont());
        int digitos = Math.max(2, String.valueOf(cantidadDeLineas()).length());
        return new Dimension(fm.charWidth('0') * digitos + 14, editor.getHeight());
    }

    @Override
    protected void paintComponent(Graphics g) {
        Rectangle vista = g.getClipBounds();
        g.setColor(FONDO);
        g.fillRect(vista.x, vista.y, vista.width, vista.height);
        g.setColor(BORDE);
        g.drawLine(getWidth() - 1, vista.y, getWidth() - 1, vista.y + vista.height);

        g.setFont(editor.getFont());
        FontMetrics fm = g.getFontMetrics();
        g.setColor(TEXTO);

        var raiz = editor.getDocument().getDefaultRootElement();
        int primera = raiz.getElementIndex(editor.viewToModel2D(new java.awt.Point(0, vista.y)));
        int ultima = raiz.getElementIndex(
                editor.viewToModel2D(new java.awt.Point(0, vista.y + vista.height)));

        for (int i = primera; i <= ultima; i++) {
            try {
                Rectangle r = editor.modelToView2D(raiz.getElement(i).getStartOffset()).getBounds();
                String numero = String.valueOf(i + 1);
                int x = getWidth() - fm.stringWidth(numero) - 7;
                g.drawString(numero, x, r.y + fm.getAscent());
            } catch (BadLocationException e) {
                return;
            }
        }
    }
}
