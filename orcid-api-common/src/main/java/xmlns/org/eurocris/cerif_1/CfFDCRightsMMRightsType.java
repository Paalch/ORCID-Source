//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4-2 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2015.10.26 at 11:29:18 AM GMT 
//


package xmlns.org.eurocris.cerif_1;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for cfFDCRightsMMRights__Type complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="cfFDCRightsMMRights__Type">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="cfDCId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="cfDCScheme" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="cfDCLangTag" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="cfDCTrans" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="cfFDCRightsConstraint" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "cfFDCRightsMMRights__Type", propOrder = {
    "cfDCId",
    "cfDCScheme",
    "cfDCLangTag",
    "cfDCTrans",
    "cfFDCRightsConstraint"
})
public class CfFDCRightsMMRightsType {

    @XmlElement(required = true)
    protected String cfDCId;
    @XmlElement(required = true)
    protected String cfDCScheme;
    @XmlElement(required = true)
    protected String cfDCLangTag;
    @XmlElement(required = true)
    protected String cfDCTrans;
    protected String cfFDCRightsConstraint;

    /**
     * Gets the value of the cfDCId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCfDCId() {
        return cfDCId;
    }

    /**
     * Sets the value of the cfDCId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCfDCId(String value) {
        this.cfDCId = value;
    }

    /**
     * Gets the value of the cfDCScheme property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCfDCScheme() {
        return cfDCScheme;
    }

    /**
     * Sets the value of the cfDCScheme property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCfDCScheme(String value) {
        this.cfDCScheme = value;
    }

    /**
     * Gets the value of the cfDCLangTag property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCfDCLangTag() {
        return cfDCLangTag;
    }

    /**
     * Sets the value of the cfDCLangTag property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCfDCLangTag(String value) {
        this.cfDCLangTag = value;
    }

    /**
     * Gets the value of the cfDCTrans property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCfDCTrans() {
        return cfDCTrans;
    }

    /**
     * Sets the value of the cfDCTrans property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCfDCTrans(String value) {
        this.cfDCTrans = value;
    }

    /**
     * Gets the value of the cfFDCRightsConstraint property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCfFDCRightsConstraint() {
        return cfFDCRightsConstraint;
    }

    /**
     * Sets the value of the cfFDCRightsConstraint property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCfFDCRightsConstraint(String value) {
        this.cfFDCRightsConstraint = value;
    }

}
