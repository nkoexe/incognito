package org.incognito.GUI.theme;

import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.ColorFunctions;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Properties;

/**
 * Modern Apple/macOS inspired theme for the Incognito Chat application.
 * Provides consistent styling across all UI components.
 */
public class ModernTheme {
    
    // Apple-inspired color palette
    public static final Color BACKGROUND_PRIMARY = new Color(248, 248, 248);
    public static final Color BACKGROUND_SECONDARY = new Color(255, 255, 255);
    public static final Color BACKGROUND_TERTIARY = new Color(242, 242, 247);
    
    public static final Color ACCENT_BLUE = new Color(0, 122, 255);
    public static final Color ACCENT_BLUE_HOVER = new Color(0, 106, 222);
    public static final Color ACCENT_BLUE_PRESSED = new Color(0, 88, 180);
    
    public static final Color TEXT_PRIMARY = new Color(30, 30, 30);
    public static final Color TEXT_SECONDARY = new Color(99, 99, 102);
    public static final Color TEXT_TERTIARY = new Color(142, 142, 147);
    
    public static final Color SUCCESS_GREEN = new Color(52, 199, 89);
    public static final Color WARNING_ORANGE = new Color(255, 149, 0);
    public static final Color ERROR_RED = new Color(255, 59, 48);
    
    public static final Color BORDER_COLOR = new Color(216, 216, 216);
    public static final Color SEPARATOR_COLOR = new Color(235, 235, 235);
    
    // Typography
    public static final Font FONT_REGULAR = new Font("SF Pro Display", Font.PLAIN, 13);
    public static final Font FONT_MEDIUM = new Font("SF Pro Display", Font.PLAIN, 14);
    public static final Font FONT_LARGE = new Font("SF Pro Display", Font.PLAIN, 16);
    public static final Font FONT_TITLE = new Font("SF Pro Display", Font.BOLD, 18);
    public static final Font FONT_BUTTON = new Font("SF Pro Text", Font.PLAIN, 13);
    
    // Fallback fonts for systems without SF Pro
    public static final Font FONT_REGULAR_FALLBACK = new Font("Helvetica Neue", Font.PLAIN, 13);
    public static final Font FONT_MEDIUM_FALLBACK = new Font("Helvetica Neue", Font.PLAIN, 14);
    public static final Font FONT_LARGE_FALLBACK = new Font("Helvetica Neue", Font.PLAIN, 16);
    public static final Font FONT_TITLE_FALLBACK = new Font("Helvetica Neue", Font.BOLD, 18);
    public static final Font FONT_BUTTON_FALLBACK = new Font("Helvetica Neue", Font.PLAIN, 13);
    
    // Border radius
    public static final int BORDER_RADIUS = 8;
    public static final int BORDER_RADIUS_SMALL = 6;
    public static final int BORDER_RADIUS_LARGE = 12;
    
    // Spacing
    public static final int SPACING_SMALL = 8;
    public static final int SPACING_MEDIUM = 16;
    public static final int SPACING_LARGE = 24;
    
    /**
     * Initialize the modern theme for the application
     */
    public static void initialize() {
        try {
            // Set FlatLaf as the Look and Feel
            FlatLightLaf.setup();
            
            // Customize FlatLaf properties
            Properties props = new Properties();
            
            // Background colors
            props.put("@background", "#F8F8F8");
            props.put("@contentBackground", "#FFFFFF");
            props.put("@selectionBackground", "#007AFF");
            props.put("@selectionInactiveBackground", "#E5E5EA");
            
            // Text colors  
            props.put("@foreground", "#1E1E1E");
            props.put("@disabledForeground", "#8E8E93");
            
            // Border colors
            props.put("@borderColor", "#D8D8D8");
            props.put("@disabledBorderColor", "#EBEBF0");
            
            // Component specific styling
            props.put("Button.arc", String.valueOf(BORDER_RADIUS));
            props.put("Component.arc", String.valueOf(BORDER_RADIUS));
            props.put("TextComponent.arc", String.valueOf(BORDER_RADIUS));
            props.put("ScrollPane.arc", String.valueOf(BORDER_RADIUS));
            
            // Button styling
            props.put("Button.default.background", "#007AFF");
            props.put("Button.default.foreground", "#FFFFFF");
            props.put("Button.default.hoverBackground", "#006AD6");
            props.put("Button.default.pressedBackground", "#0058B4");
            props.put("Button.default.borderWidth", "0");
            
            // List styling
            props.put("List.selectionBackground", "#007AFF");
            props.put("List.selectionForeground", "#FFFFFF");
            props.put("List.background", "#FFFFFF");
            
            // TextArea styling
            props.put("TextArea.background", "#FFFFFF");
            props.put("TextField.background", "#FFFFFF");
              // Convert Properties to Map for FlatLaf
            java.util.Map<String, String> flatProps = new java.util.HashMap<>();
            for (Object key : props.keySet()) {
                flatProps.put((String) key, (String) props.get(key));
            }
            FlatLightLaf.setGlobalExtraDefaults(flatProps);
            
            // Update UI for all existing components
            SwingUtilities.invokeLater(() -> {
                for (Window window : Window.getWindows()) {
                    SwingUtilities.updateComponentTreeUI(window);
                }
            });
              } catch (Exception e) {
            e.printStackTrace();
            // Fallback to basic swing look and feel
            try {
                UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
    
    /**
     * Create a modern styled button with Apple-like appearance
     */
    public static JButton createButton(String text, ButtonType type) {
        JButton button = new JButton(text);
        styleButton(button, type);
        return button;
    }
    
    /**
     * Style an existing button with modern Apple-like appearance
     */
    public static void styleButton(JButton button, ButtonType type) {
        // Set font
        Font buttonFont = isFontAvailable("SF Pro Text") ? FONT_BUTTON : FONT_BUTTON_FALLBACK;
        button.setFont(buttonFont);
        
        // Remove default button styling
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(true);
        button.setOpaque(true);
        
        // Set colors based on button type
        switch (type) {
            case PRIMARY:
                button.setBackground(ACCENT_BLUE);
                button.setForeground(Color.WHITE);
                break;
            case SECONDARY:
                button.setBackground(BACKGROUND_SECONDARY);
                button.setForeground(TEXT_PRIMARY);
                button.setBorder(createRoundedBorder(BORDER_COLOR, 1));
                break;
            case DANGER:
                button.setBackground(ERROR_RED);
                button.setForeground(Color.WHITE);
                break;
            case SUCCESS:
                button.setBackground(SUCCESS_GREEN);
                button.setForeground(Color.WHITE);
                break;
        }
        
        // Add hover effects
        addHoverEffect(button, type);
        
        // Set padding
        button.setBorder(BorderFactory.createCompoundBorder(
            button.getBorder(),
            BorderFactory.createEmptyBorder(SPACING_SMALL, SPACING_MEDIUM, SPACING_SMALL, SPACING_MEDIUM)
        ));
    }
    
    /**
     * Create a modern styled panel with rounded corners
     */
    public static JPanel createPanel() {
        JPanel panel = new JPanel();
        stylePanel(panel);
        return panel;
    }
    
    /**
     * Style an existing panel with modern appearance
     */
    public static void stylePanel(JPanel panel) {
        panel.setBackground(BACKGROUND_SECONDARY);
        panel.setBorder(createRoundedBorder(BORDER_COLOR, 1));
    }
    
    /**
     * Create a modern styled text field
     */
    public static JTextField createTextField() {
        JTextField textField = new JTextField();
        styleTextField(textField);
        return textField;
    }
    
    /**
     * Style an existing text field
     */
    public static void styleTextField(JTextField textField) {
        Font fieldFont = isFontAvailable("SF Pro Text") ? FONT_REGULAR : FONT_REGULAR_FALLBACK;
        textField.setFont(fieldFont);
        textField.setBackground(BACKGROUND_SECONDARY);
        textField.setForeground(TEXT_PRIMARY);
        textField.setBorder(BorderFactory.createCompoundBorder(
            createRoundedBorder(BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(SPACING_SMALL, SPACING_MEDIUM, SPACING_SMALL, SPACING_MEDIUM)
        ));
    }
    
    /**
     * Create a modern styled text area
     */
    public static JTextArea createTextArea() {
        JTextArea textArea = new JTextArea();
        styleTextArea(textArea);
        return textArea;
    }
    
    /**
     * Style an existing text area
     */
    public static void styleTextArea(JTextArea textArea) {
        Font areaFont = isFontAvailable("SF Pro Text") ? FONT_REGULAR : FONT_REGULAR_FALLBACK;
        textArea.setFont(areaFont);
        textArea.setBackground(BACKGROUND_SECONDARY);
        textArea.setForeground(TEXT_PRIMARY);
        textArea.setBorder(BorderFactory.createCompoundBorder(
            createRoundedBorder(BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(SPACING_MEDIUM, SPACING_MEDIUM, SPACING_MEDIUM, SPACING_MEDIUM)
        ));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
    }
    
    /**
     * Create a modern styled label
     */
    public static JLabel createLabel(String text, LabelType type) {
        JLabel label = new JLabel(text);
        styleLabel(label, type);
        return label;
    }
    
    /**
     * Style an existing label
     */
    public static void styleLabel(JLabel label, LabelType type) {
        switch (type) {
            case TITLE:
                Font titleFont = isFontAvailable("SF Pro Display") ? FONT_TITLE : FONT_TITLE_FALLBACK;
                label.setFont(titleFont);
                label.setForeground(TEXT_PRIMARY);
                break;
            case BODY:
                Font bodyFont = isFontAvailable("SF Pro Text") ? FONT_MEDIUM : FONT_MEDIUM_FALLBACK;
                label.setFont(bodyFont);
                label.setForeground(TEXT_PRIMARY);
                break;
            case CAPTION:
                Font captionFont = isFontAvailable("SF Pro Text") ? FONT_REGULAR : FONT_REGULAR_FALLBACK;
                label.setFont(captionFont);
                label.setForeground(TEXT_SECONDARY);
                break;
        }
    }
    
    /**
     * Create a rounded border
     */
    public static Border createRoundedBorder(Color color, int thickness) {
        return new RoundedBorder(color, thickness, BORDER_RADIUS);
    }
    
    /**
     * Add hover effect to a button
     */
    private static void addHoverEffect(JButton button, ButtonType type) {
        Color originalBackground = button.getBackground();
        Color hoverBackground;
        
        switch (type) {
            case PRIMARY:
                hoverBackground = ACCENT_BLUE_HOVER;
                break;
            case SECONDARY:
                hoverBackground = BACKGROUND_TERTIARY;
                break;
            case DANGER:
                hoverBackground = ColorFunctions.lighten(ERROR_RED, 0.1f);
                break;
            case SUCCESS:
                hoverBackground = ColorFunctions.lighten(SUCCESS_GREEN, 0.1f);
                break;
            default:
                hoverBackground = originalBackground;
        }
        
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(hoverBackground);
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(originalBackground);
                }
            }
        });
    }
    
    /**
     * Check if a font is available on the system
     */
    private static boolean isFontAvailable(String fontName) {
        String[] availableFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String font : availableFonts) {
            if (font.equals(fontName)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Button type enumeration
     */
    public enum ButtonType {
        PRIMARY, SECONDARY, DANGER, SUCCESS
    }
    
    /**
     * Label type enumeration
     */
    public enum LabelType {
        TITLE, BODY, CAPTION
    }
    
    /**
     * Custom rounded border implementation
     */
    private static class RoundedBorder implements Border {
        private final Color color;
        private final int thickness;
        private final int radius;
        
        public RoundedBorder(Color color, int thickness, int radius) {
            this.color = color;
            this.thickness = thickness;
            this.radius = radius;
        }
        
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(thickness));
            g2.drawRoundRect(x + thickness/2, y + thickness/2, 
                           width - thickness, height - thickness, radius, radius);
            g2.dispose();
        }
        
        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(thickness, thickness, thickness, thickness);
        }
        
        @Override
        public boolean isBorderOpaque() {
            return false;
        }
    }
}
