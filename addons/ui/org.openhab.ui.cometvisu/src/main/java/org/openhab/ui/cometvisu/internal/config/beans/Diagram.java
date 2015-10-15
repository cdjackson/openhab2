//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4-2 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2015.04.17 at 05:50:37 PM CEST 
//

package org.openhab.ui.cometvisu.internal.config.beans;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

/**
 * <p>
 * Java class for diagram complex type.
 * 
 * <p>
 * The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="diagram">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="layout" type="{}layout" minOccurs="0"/>
 *         &lt;element name="label" type="{}label" minOccurs="0"/>
 *         &lt;element name="axis" type="{}axis" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="rrd" type="{}rrd" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *       &lt;attribute name="width" type="{}dimension" />
 *       &lt;attribute name="height" type="{}dimension" />
 *       &lt;attribute name="popup" type="{http://www.w3.org/2001/XMLSchema}boolean" />
 *       &lt;attribute name="previewlabels" type="{http://www.w3.org/2001/XMLSchema}boolean" />
 *       &lt;attribute ref="{}series"/>
 *       &lt;attribute name="refresh" type="{http://www.w3.org/2001/XMLSchema}integer" />
 *       &lt;attribute name="period" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="gridcolor" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute ref="{}legend"/>
 *       &lt;attribute ref="{}legendposition"/>
 *       &lt;attribute name="title" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="tooltip" type="{http://www.w3.org/2001/XMLSchema}boolean" />
 *       &lt;attribute ref="{}timeformat"/>
 *       &lt;attribute ref="{}timeformatTooltip"/>
 *       &lt;attribute name="zoomYAxis" type="{http://www.w3.org/2001/XMLSchema}boolean" />
 *       &lt;attribute ref="{}seriesStart"/>
 *       &lt;attribute ref="{}seriesEnd"/>
 *       &lt;attribute ref="{}seriesResolution"/>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "diagram", propOrder = { "layout", "label", "axis", "rrd" })
public class Diagram {

    protected Layout layout;
    protected Label label;
    protected List<Axis> axis;
    protected List<Rrd> rrd;
    @XmlAttribute(name = "width")
    protected String width;
    @XmlAttribute(name = "height")
    protected String height;
    @XmlAttribute(name = "popup")
    protected Boolean popup;
    @XmlAttribute(name = "previewlabels")
    protected Boolean previewlabels;
    @XmlAttribute(name = "series")
    protected String series;
    @XmlAttribute(name = "refresh")
    protected BigInteger refresh;
    @XmlAttribute(name = "period")
    protected String period;
    @XmlAttribute(name = "gridcolor")
    protected String gridcolor;
    @XmlAttribute(name = "legend")
    protected String legend;
    @XmlAttribute(name = "legendposition")
    protected String legendposition;
    @XmlAttribute(name = "title")
    protected String title;
    @XmlAttribute(name = "tooltip")
    protected Boolean tooltip;
    @XmlAttribute(name = "timeformat")
    protected String timeformat;
    @XmlAttribute(name = "timeformatTooltip")
    protected String timeformatTooltip;
    @XmlAttribute(name = "zoomYAxis")
    protected Boolean zoomYAxis;
    @XmlAttribute(name = "seriesStart")
    protected String seriesStart;
    @XmlAttribute(name = "seriesEnd")
    protected String seriesEnd;
    @XmlAttribute(name = "seriesResolution")
    protected BigInteger seriesResolution;

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
     * Gets the value of the axis property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the axis property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * 
     * <pre>
     * getAxis().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link Axis }
     * 
     * 
     */
    public List<Axis> getAxis() {
        if (axis == null) {
            axis = new ArrayList<Axis>();
        }
        return this.axis;
    }

    /**
     * Gets the value of the rrd property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the rrd property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * 
     * <pre>
     * getRrd().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link Rrd }
     * 
     * 
     */
    public List<Rrd> getRrd() {
        if (rrd == null) {
            rrd = new ArrayList<Rrd>();
        }
        return this.rrd;
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
     * Gets the value of the popup property.
     * 
     * @return
     *         possible object is
     *         {@link Boolean }
     * 
     */
    public Boolean isPopup() {
        return popup;
    }

    /**
     * Sets the value of the popup property.
     * 
     * @param value
     *            allowed object is
     *            {@link Boolean }
     * 
     */
    public void setPopup(Boolean value) {
        this.popup = value;
    }

    /**
     * Gets the value of the previewlabels property.
     * 
     * @return
     *         possible object is
     *         {@link Boolean }
     * 
     */
    public Boolean isPreviewlabels() {
        return previewlabels;
    }

    /**
     * Sets the value of the previewlabels property.
     * 
     * @param value
     *            allowed object is
     *            {@link Boolean }
     * 
     */
    public void setPreviewlabels(Boolean value) {
        this.previewlabels = value;
    }

    /**
     * Gets the value of the series property.
     * 
     * @return
     *         possible object is
     *         {@link String }
     * 
     */
    public String getSeries() {
        return series;
    }

    /**
     * Sets the value of the series property.
     * 
     * @param value
     *            allowed object is
     *            {@link String }
     * 
     */
    public void setSeries(String value) {
        this.series = value;
    }

    /**
     * Gets the value of the refresh property.
     * 
     * @return
     *         possible object is
     *         {@link BigInteger }
     * 
     */
    public BigInteger getRefresh() {
        return refresh;
    }

    /**
     * Sets the value of the refresh property.
     * 
     * @param value
     *            allowed object is
     *            {@link BigInteger }
     * 
     */
    public void setRefresh(BigInteger value) {
        this.refresh = value;
    }

    /**
     * Gets the value of the period property.
     * 
     * @return
     *         possible object is
     *         {@link String }
     * 
     */
    public String getPeriod() {
        return period;
    }

    /**
     * Sets the value of the period property.
     * 
     * @param value
     *            allowed object is
     *            {@link String }
     * 
     */
    public void setPeriod(String value) {
        this.period = value;
    }

    /**
     * Gets the value of the gridcolor property.
     * 
     * @return
     *         possible object is
     *         {@link String }
     * 
     */
    public String getGridcolor() {
        return gridcolor;
    }

    /**
     * Sets the value of the gridcolor property.
     * 
     * @param value
     *            allowed object is
     *            {@link String }
     * 
     */
    public void setGridcolor(String value) {
        this.gridcolor = value;
    }

    /**
     * Gets the value of the legend property.
     * 
     * @return
     *         possible object is
     *         {@link String }
     * 
     */
    public String getLegend() {
        return legend;
    }

    /**
     * Sets the value of the legend property.
     * 
     * @param value
     *            allowed object is
     *            {@link String }
     * 
     */
    public void setLegend(String value) {
        this.legend = value;
    }

    /**
     * Gets the value of the legendposition property.
     * 
     * @return
     *         possible object is
     *         {@link String }
     * 
     */
    public String getLegendposition() {
        return legendposition;
    }

    /**
     * Sets the value of the legendposition property.
     * 
     * @param value
     *            allowed object is
     *            {@link String }
     * 
     */
    public void setLegendposition(String value) {
        this.legendposition = value;
    }

    /**
     * Gets the value of the title property.
     * 
     * @return
     *         possible object is
     *         {@link String }
     * 
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the value of the title property.
     * 
     * @param value
     *            allowed object is
     *            {@link String }
     * 
     */
    public void setTitle(String value) {
        this.title = value;
    }

    /**
     * Gets the value of the tooltip property.
     * 
     * @return
     *         possible object is
     *         {@link Boolean }
     * 
     */
    public Boolean isTooltip() {
        return tooltip;
    }

    /**
     * Sets the value of the tooltip property.
     * 
     * @param value
     *            allowed object is
     *            {@link Boolean }
     * 
     */
    public void setTooltip(Boolean value) {
        this.tooltip = value;
    }

    /**
     * Gets the value of the timeformat property.
     * 
     * @return
     *         possible object is
     *         {@link String }
     * 
     */
    public String getTimeformat() {
        return timeformat;
    }

    /**
     * Sets the value of the timeformat property.
     * 
     * @param value
     *            allowed object is
     *            {@link String }
     * 
     */
    public void setTimeformat(String value) {
        this.timeformat = value;
    }

    /**
     * Gets the value of the timeformatTooltip property.
     * 
     * @return
     *         possible object is
     *         {@link String }
     * 
     */
    public String getTimeformatTooltip() {
        return timeformatTooltip;
    }

    /**
     * Sets the value of the timeformatTooltip property.
     * 
     * @param value
     *            allowed object is
     *            {@link String }
     * 
     */
    public void setTimeformatTooltip(String value) {
        this.timeformatTooltip = value;
    }

    /**
     * Gets the value of the zoomYAxis property.
     * 
     * @return
     *         possible object is
     *         {@link Boolean }
     * 
     */
    public Boolean isZoomYAxis() {
        return zoomYAxis;
    }

    /**
     * Sets the value of the zoomYAxis property.
     * 
     * @param value
     *            allowed object is
     *            {@link Boolean }
     * 
     */
    public void setZoomYAxis(Boolean value) {
        this.zoomYAxis = value;
    }

    /**
     * Gets the value of the seriesStart property.
     * 
     * @return
     *         possible object is
     *         {@link String }
     * 
     */
    public String getSeriesStart() {
        return seriesStart;
    }

    /**
     * Sets the value of the seriesStart property.
     * 
     * @param value
     *            allowed object is
     *            {@link String }
     * 
     */
    public void setSeriesStart(String value) {
        this.seriesStart = value;
    }

    /**
     * Gets the value of the seriesEnd property.
     * 
     * @return
     *         possible object is
     *         {@link String }
     * 
     */
    public String getSeriesEnd() {
        return seriesEnd;
    }

    /**
     * Sets the value of the seriesEnd property.
     * 
     * @param value
     *            allowed object is
     *            {@link String }
     * 
     */
    public void setSeriesEnd(String value) {
        this.seriesEnd = value;
    }

    /**
     * Gets the value of the seriesResolution property.
     * 
     * @return
     *         possible object is
     *         {@link BigInteger }
     * 
     */
    public BigInteger getSeriesResolution() {
        return seriesResolution;
    }

    /**
     * Sets the value of the seriesResolution property.
     * 
     * @param value
     *            allowed object is
     *            {@link BigInteger }
     * 
     */
    public void setSeriesResolution(BigInteger value) {
        this.seriesResolution = value;
    }

}
