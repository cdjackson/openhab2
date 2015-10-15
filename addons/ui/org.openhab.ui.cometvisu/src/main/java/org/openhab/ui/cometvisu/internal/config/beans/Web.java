//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4-2 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2015.04.17 at 05:50:37 PM CEST 
//

package org.openhab.ui.cometvisu.internal.config.beans;

import java.math.BigDecimal;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

/**
 * <p>
 * Java class for web complex type.
 * 
 * <p>
 * The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="web">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="layout" type="{}layout" minOccurs="0"/>
 *         &lt;element name="label" type="{}label" minOccurs="0"/>
 *       &lt;/sequence>
 *       &lt;attribute name="src" use="required" type="{}uri" />
 *       &lt;attribute name="width" type="{}dimension" />
 *       &lt;attribute name="height" type="{}dimension" />
 *       &lt;attribute name="frameborder" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="background" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="refresh" type="{http://www.w3.org/2001/XMLSchema}decimal" />
 *       &lt;attribute name="scrolling">
 *         &lt;simpleType>
 *           &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *             &lt;enumeration value="yes"/>
 *             &lt;enumeration value="no"/>
 *             &lt;enumeration value="auto"/>
 *           &lt;/restriction>
 *         &lt;/simpleType>
 *       &lt;/attribute>
 *       &lt;attribute name="ga" type="{}addr" />
 *       &lt;attribute ref="{}flavour"/>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "web", propOrder = { "layout", "label" })
public class Web {

    protected Layout layout;
    protected Label label;
    @XmlAttribute(name = "src", required = true)
    protected String src;
    @XmlAttribute(name = "width")
    protected String width;
    @XmlAttribute(name = "height")
    protected String height;
    @XmlAttribute(name = "frameborder")
    protected String frameborder;
    @XmlAttribute(name = "background")
    protected String background;
    @XmlAttribute(name = "refresh")
    protected BigDecimal refresh;
    @XmlAttribute(name = "scrolling")
    protected String scrolling;
    @XmlAttribute(name = "ga")
    protected String ga;
    @XmlAttribute(name = "flavour")
    protected String flavour;

    /**
     * Gets the value of the layout property.
     * 
     * @return
     *         possible object is
     *         {@link Layout }
     * 
     */
    public Layout getLayout() {
        return layout;
    }

    /**
     * Sets the value of the layout property.
     * 
     * @param value
     *            allowed object is
     *            {@link Layout }
     * 
     */
    public void setLayout(Layout value) {
        this.layout = value;
    }

    /**
     * Gets the value of the label property.
     * 
     * @return
     *         possible object is
     *         {@link Label }
     * 
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Sets the value of the label property.
     * 
     * @param value
     *            allowed object is
     *            {@link Label }
     * 
     */
    public void setLabel(Label value) {
        this.label = value;
    }

    /**
     * Gets the value of the src property.
     * 
     * @return
     *         possible object is
     *         {@link String }
     * 
     */
    public String getSrc() {
        return src;
    }

    /**
     * Sets the value of the src property.
     * 
     * @param value
     *            allowed object is
     *            {@link String }
     * 
     */
    public void setSrc(String value) {
        this.src = value;
    }

    /**
     * Gets the value of the width property.
     * 
     * @return
     *         possible object is
     *         {@link String }
     * 
     */
    public String getWidth() {
        return width;
    }

    /**
     * Sets the value of the width property.
     * 
     * @param value
     *            allowed object is
     *            {@link String }
     * 
     */
    public void setWidth(String value) {
        this.width = value;
    }

    /**
     * Gets the value of the height property.
     * 
     * @return
     *         possible object is
     *         {@link String }
     * 
     */
    public String getHeight() {
        return height;
    }

    /**
     * Sets the value of the height property.
     * 
     * @param value
     *            allowed object is
     *            {@link String }
     * 
     */
    public void setHeight(String value) {
        this.height = value;
    }

    /**
     * Gets the value of the frameborder property.
     * 
     * @return
     *         possible object is
     *         {@link String }
     * 
     */
    public String getFrameborder() {
        return frameborder;
    }

    /**
     * Sets the value of the frameborder property.
     * 
     * @param value
     *            allowed object is
     *            {@link String }
     * 
     */
    public void setFrameborder(String value) {
        this.frameborder = value;
    }

    /**
     * Gets the value of the background property.
     * 
     * @return
     *         possible object is
     *         {@link String }
     * 
     */
    public String getBackground() {
        return background;
    }

    /**
     * Sets the value of the background property.
     * 
     * @param value
     *            allowed object is
     *            {@link String }
     * 
     */
    public void setBackground(String value) {
        this.background = value;
    }

    /**
     * Gets the value of the refresh property.
     * 
     * @return
     *         possible object is
     *         {@link BigDecimal }
     * 
     */
    public BigDecimal getRefresh() {
        return refresh;
    }

    /**
     * Sets the value of the refresh property.
     * 
     * @param value
     *            allowed object is
     *            {@link BigDecimal }
     * 
     */
    public void setRefresh(BigDecimal value) {
        this.refresh = value;
    }

    /**
     * Gets the value of the scrolling property.
     * 
     * @return
     *         possible object is
     *         {@link String }
     * 
     */
    public String getScrolling() {
        return scrolling;
    }

    /**
     * Sets the value of the scrolling property.
     * 
     * @param value
     *            allowed object is
     *            {@link String }
     * 
     */
    public void setScrolling(String value) {
        this.scrolling = value;
    }

    /**
     * Gets the value of the ga property.
     * 
     * @return
     *         possible object is
     *         {@link String }
     * 
     */
    public String getGa() {
        return ga;
    }

    /**
     * Sets the value of the ga property.
     * 
     * @param value
     *            allowed object is
     *            {@link String }
     * 
     */
    public void setGa(String value) {
        this.ga = value;
    }

    /**
     * Gets the value of the flavour property.
     * 
     * @return
     *         possible object is
     *         {@link String }
     * 
     */
    public String getFlavour() {
        return flavour;
    }

    /**
     * Sets the value of the flavour property.
     * 
     * @param value
     *            allowed object is
     *            {@link String }
     * 
     */
    public void setFlavour(String value) {
        this.flavour = value;
    }

}
