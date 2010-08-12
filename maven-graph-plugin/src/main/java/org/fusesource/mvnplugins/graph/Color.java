/*
 * $Id$ 
 */
package org.fusesource.mvnplugins.graph;


/**
 * @author Marcel Patzlaff
 * @version $Revision:$
 */
public class Color {
    private final static char[] DIGITS = {'0' , '1' , '2' , '3' , '4' , '5' , '6' , '7' , '8' , '9' , 'A' , 'B' , 'C' , 'D' , 'E' , 'F'};
    
    public static final Color CORN_FLOWER_BLUE= new Color(0x64, 0x95, 0xED); 
    public static final Color GREEN= new Color(0x00, 0x80, 0x00);
    public static final Color DARKGREY= new Color(0xA9, 0xA9, 0xA9);
    
    public static final Color BLACK= new Color(0x00, 0x00, 0x00);
    public static final Color WHITE= new Color(0xFF, 0xFF, 0xFF);
    
    public static final Color ROOT_COLOR= new Color(0xDD, 0xDD, 0xDD);
    
    public final int red;
    public final int green;
    public final int blue;
    
    public final String hexStr;
    
    public static Color decode(String rgbHexStr) {
        Integer val= Integer.decode(rgbHexStr);
        int i= val.intValue();
        return new Color((i >> 16) & 0xFF, (i >> 8) & 0xFF, i & 0xFF);
    }
    
    private Color(int red, int green, int blue) {
        this.red= red;
        this.green= green;
        this.blue= blue;
        this.hexStr= createHexString();
    }

    public String toString() {
        return this.hexStr;
    }
    
    public Color mix(Color other) {
        if(other == null) {
            return this;
        }
        
        return new Color(
            (red + other.red) / 2,
            (green + other.green) / 2,
            (blue + other.blue) / 2
        );
    }
    
    private String createHexString() {
        char[] buf = new char[7];
        buf[0]= '#';
        int i= (red << 16) | (green << 8) | (blue);
        int charPos = 6;
        int shift= 4;
        int radix = 1 << shift;
        int mask = radix - 1;
        
        do {
            buf[charPos--] = DIGITS[i & mask];
            i >>>= shift;
        } while (charPos > 0);

        return new String(buf);
    }
}
