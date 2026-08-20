package rinfo.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import rinfo.lang.TipoToken;

/**
 * Editor del fuente con coloreado de sintaxis.
 *
 * <p>Las palabras que se resaltan salen de {@link TipoToken}, así que no hay
 * una segunda lista de palabras reservadas que se pueda desincronizar del
 * scanner. Como el lenguaje es sensible a la indentación, la tecla Tab inserta
 * dos espacios y Enter mantiene la sangría de la línea anterior.
 */
public final class EditorRInfo extends JTextPane {

    private static final Color COLOR_ESTRUCTURA = new Color(0x7B1FA2);
    private static final Color COLOR_CONTROL = new Color(0x0D47A1);
    private static final Color COLOR_PRIMITIVA = new Color(0x00695C);
    private static final Color COLOR_CONSULTA = new Color(0xC2185B);
    private static final Color COLOR_TIPO = new Color(0xE65100);
    private static final Color COLOR_NUMERO = new Color(0x1565C0);
    private static final Color COLOR_COMENTARIO = new Color(0x7A8B7A);
    private static final Color COLOR_TEXTO_LITERAL = new Color(0x2E7D32);

    private static final Set<TipoToken> ESTRUCTURA = EnumSet.of(
            TipoToken.PROGRAMA, TipoToken.PROCESOS, TipoToken.PROCESO, TipoToken.AREAS,
            TipoToken.ROBOTS, TipoToken.ROBOT, TipoToken.VARIABLES, TipoToken.COMENZAR,
            TipoToken.FIN);

    private static final Set<TipoToken> CONTROL = EnumSet.of(
            TipoToken.SI, TipoToken.SINO, TipoToken.MIENTRAS, TipoToken.REPETIR);

    private static final Set<TipoToken> PRIMITIVAS = EnumSet.of(
            TipoToken.MOVER, TipoToken.DERECHA, TipoToken.TOMARFLOR, TipoToken.TOMARPAPEL,
            TipoToken.DEPOSITARFLOR, TipoToken.DEPOSITARPAPEL, TipoToken.POS, TipoToken.INFORMAR,
            TipoToken.INICIAR, TipoToken.ASIGNARAREA, TipoToken.ENVIARMENSAJE,
            TipoToken.RECIBIRMENSAJE, TipoToken.BLOQUEARESQUINA, TipoToken.LIBERARESQUINA,
            TipoToken.LEER, TipoToken.RANDOM);

    private static final Set<TipoToken> CONSULTAS = EnumSet.of(
            TipoToken.POSAV, TipoToken.POSCA, TipoToken.HAYFLORENLAESQUINA,
            TipoToken.HAYFLORENLABOLSA, TipoToken.HAYPAPELENLAESQUINA,
            TipoToken.HAYPAPELENLABOLSA, TipoToken.HAYOBSTACULO);

    private static final Set<TipoToken> TIPOS = EnumSet.of(
            TipoToken.NUMERO, TipoToken.BOOLEAN, TipoToken.ENTRADA, TipoToken.SALIDA,
            TipoToken.ENTRADASALIDA, TipoToken.AREAC, TipoToken.AREAP, TipoToken.AREAPC,
            TipoToken.VERDADERO, TipoToken.FALSO);

    private static final Pattern PALABRAS = Pattern.compile("[A-Za-zÁÉÍÓÚáéíóúÑñ][A-Za-z0-9_.]*");
    private static final Pattern NUMEROS = Pattern.compile("\\b\\d+\\b");
    private static final Pattern COMENTARIOS = Pattern.compile("\\{[^}]*\\}?", Pattern.DOTALL);
    private static final Pattern LITERALES = Pattern.compile("'[^'\\n]*'?");

    private final Map<String, SimpleAttributeSet> estilosPorPalabra = new HashMap<>();
    private final SimpleAttributeSet estiloNormal = new SimpleAttributeSet();
    private final SimpleAttributeSet estiloNumero = new SimpleAttributeSet();
    private final SimpleAttributeSet estiloComentario = new SimpleAttributeSet();
    private final SimpleAttributeSet estiloLiteral = new SimpleAttributeSet();

    private boolean coloreando;

    public EditorRInfo() {
        setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        prepararEstilos();
        configurarTeclas();

        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                pedirColoreado();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                pedirColoreado();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // sólo cambian atributos: no hace falta recolorear
            }
        });
    }

    private void prepararEstilos() {
        StyleConstants.setForeground(estiloNormal, new Color(0x22282A));
        StyleConstants.setBold(estiloNormal, false);

        StyleConstants.setForeground(estiloNumero, COLOR_NUMERO);
        StyleConstants.setForeground(estiloComentario, COLOR_COMENTARIO);
        StyleConstants.setItalic(estiloComentario, true);
        StyleConstants.setForeground(estiloLiteral, COLOR_TEXTO_LITERAL);

        registrar(ESTRUCTURA, COLOR_ESTRUCTURA, true);
        registrar(CONTROL, COLOR_CONTROL, true);
        registrar(PRIMITIVAS, COLOR_PRIMITIVA, false);
        registrar(CONSULTAS, COLOR_CONSULTA, false);
        registrar(TIPOS, COLOR_TIPO, false);
    }

    private void registrar(Set<TipoToken> tokens, Color color, boolean negrita) {
        SimpleAttributeSet estilo = new SimpleAttributeSet();
        StyleConstants.setForeground(estilo, color);
        StyleConstants.setBold(estilo, negrita);
        for (TipoToken t : tokens) {
            estilosPorPalabra.put(t.escritura, estilo);
        }
    }

    private void configurarTeclas() {
        getInputMap().put(KeyStroke.getKeyStroke("TAB"), "sangrar");
        getActionMap().put("sangrar", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                replaceSelection("  ");
            }
        });

        getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "nuevaLinea");
        getActionMap().put("nuevaLinea", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                replaceSelection("\n" + sangriaDeLaLineaActual());
            }
        });
    }

    private String sangriaDeLaLineaActual() {
        try {
            String texto = getDocument().getText(0, getCaretPosition());
            int inicio = texto.lastIndexOf('\n') + 1;
            StringBuilder sangria = new StringBuilder();
            for (int i = inicio; i < texto.length() && texto.charAt(i) == ' '; i++) {
                sangria.append(' ');
            }
            return sangria.toString();
        } catch (BadLocationException e) {
            return "";
        }
    }

    private void pedirColoreado() {
        if (coloreando) {
            return;
        }
        SwingUtilities.invokeLater(this::colorear);
    }

    /** Reaplica los estilos a todo el documento. */
    public void colorear() {
        StyledDocument doc = getStyledDocument();
        String texto;
        try {
            texto = doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            return;
        }

        coloreando = true;
        try {
            doc.setCharacterAttributes(0, texto.length(), estiloNormal, true);

            Matcher palabras = PALABRAS.matcher(texto);
            while (palabras.find()) {
                SimpleAttributeSet estilo = estilosPorPalabra.get(palabras.group());
                if (estilo != null) {
                    doc.setCharacterAttributes(palabras.start(),
                            palabras.end() - palabras.start(), estilo, true);
                }
            }

            Matcher numeros = NUMEROS.matcher(texto);
            while (numeros.find()) {
                doc.setCharacterAttributes(numeros.start(),
                        numeros.end() - numeros.start(), estiloNumero, true);
            }

            // Literales y comentarios pisan lo anterior: son los que mandan.
            Matcher literales = LITERALES.matcher(texto);
            while (literales.find()) {
                doc.setCharacterAttributes(literales.start(),
                        literales.end() - literales.start(), estiloLiteral, true);
            }

            Matcher comentarios = COMENTARIOS.matcher(texto);
            while (comentarios.find()) {
                doc.setCharacterAttributes(comentarios.start(),
                        comentarios.end() - comentarios.start(), estiloComentario, true);
            }
        } finally {
            coloreando = false;
        }
    }

    /** Selecciona una línea completa, para marcar dónde está el error. */
    public void marcarLinea(int fila) {
        String texto = getText().replace("\r\n", "\n");
        int inicio = 0;
        int lineaActual = 1;
        while (lineaActual < fila) {
            int salto = texto.indexOf('\n', inicio);
            if (salto < 0) {
                return;
            }
            inicio = salto + 1;
            lineaActual++;
        }
        int fin = texto.indexOf('\n', inicio);
        if (fin < 0) {
            fin = texto.length();
        }
        requestFocusInWindow();
        setCaretPosition(inicio);
        moveCaretPosition(fin);
    }
}
