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
 * <p>Java class for cfFundKeyw__Type complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="cfFundKeyw__Type">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="cfFundId" type="{urn:xmlns:org:eurocris:cerif-1.6-2}cfId__Type"/>
 *         &lt;element name="cfKeyw" type="{urn:xmlns:org:eurocris:cerif-1.6-2}cfMLangString__Type"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "cfFundKeyw__Type", propOrder = {
    "cfFundId",
    "cfKeyw"
})
public class CfFundKeywType {

    @XmlElement(required = true)
    protected String cfFundId;
    @XmlElement(required = true)
    protected CfMLangStringType cfKeyw;

    /**
     * Gets the value of the cfFundId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCfFundId() {
        return cfFundId;
    }

    /**
     * Sets the value of the cfFundId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCfFundId(String value) {
        this.cfFundId = value;
    }

    /**
     * Gets the value of the cfKeyw property.
     * 
     * @return
     *     possible object is
     *     {@link CfMLangStringType }
     *     
     */
    public CfMLangStringType getCfKeyw() {
        return cfKeyw;
    }

    /**
     * Sets the value of the cfKeyw property.
     * 
     * @param value
     *     allowed object is
     *     {@link CfMLangStringType }
     *     
     */
    public void setCfKeyw(CfMLangStringType value) {
        this.cfKeyw = value;
    }

}
