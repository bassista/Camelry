package me.alanfoster.camelry.codegen

import javax.xml.bind.JAXBContext
import me.alanfoster.camelry.codegen.model._
import me.alanfoster.camelry.codegen.model.BeanInfo
import me.alanfoster.camelry.codegen.model.GeneratorObject
import me.alanfoster.camelry.codegen.model.Metadata
import scala.collection.{mutable, immutable, JavaConversions}

import com.sun.xml.bind.v2.runtime.JAXBContextImpl
import com.sun.xml.bind.v2.model.runtime._
import com.sun.xml.bind.v2.model.impl.RuntimeElementPropertyInfoImpl

import scala.collection.JavaConverters._
import com.sun.xml.bind.v2.model.core.{ElementPropertyInfo, AttributePropertyInfo}
import java.lang.reflect.{ParameterizedType, Method, Type}

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util


// TODO Consider the scenario of isAbstract() being true

/**
 * The trait Generator for creating DomElement interfaces from JAXB annotated classes.
 * The generatorName itself should perform any data transforms required before passing
 * to a concrete implementation of #generate
 */
trait Generator {
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  /**
   *
   * @param jaxbPaths The packages containing the jaxb.index file
   *                  eg "foo.bar.baz" implies there exists a file "foo/bar/baz/jaxb.index"
   */
  def generateFiles(metadata: Metadata, jaxbPaths: String*): List[String] = {
    val delimitedPaths: String = jaxbPaths.mkString(":")

    val context: JAXBContext = JAXBContext.newInstance(delimitedPaths)

    val set: RuntimeTypeInfoSet = context.asInstanceOf[JAXBContextImpl].getTypeInfoSet
    val beans: mutable.Map[Class[_], _ <: RuntimeClassInfo] = set.beans().asScala

    val files = beans
      //.filter({ case (key, value) => key.getSimpleName == "PersonDatabase"})
      //.filter({ case (key, value) => key.getSimpleName == "AggregateDefinition"})
      //.filter({ case (key, value) => key.getSimpleName == "PersonDatabase"})
      //.filter({ case (key, value) => key.getSimpleName == "CatchDefinition"})
      //.filter({ case (key, value) => value.isElement && "aggregate".equals(value.getElementName.getLocalPart) })
      .map({
      case (clazz, clazzInfo) => generateFile(metadata, clazz, clazzInfo)
    })
    files.toList
  }

  def generateFile(metadata: Metadata, clazz: Class[_], classInfo: RuntimeClassInfo): String = {
    logger.info("Generating file for {}", clazz)

    // Data transformation
    val properties: mutable.Buffer[_ <: RuntimePropertyInfo] = classInfo.getProperties.asScala

    val propertyMap = PropertyMapper.groupProperties(classInfo)

    if(propertyMap("values").size > 1) {
      throw new IllegalArgumentException("Values list should be zero or one :: " + propertyMap("values").mkString)
    }

    val generatedText = generate(
      new GeneratorObject(
        generatorName = String.valueOf(getClass),
        metadata = metadata,
        beanInfo = new BeanInfo(
          simpleName = clazz.getSimpleName,
          xmlName =
              if(classInfo.getElementName == null) "AbstractClass"
              else classInfo.getElementName.getLocalPart,
          baseClass = Option(classInfo.getBaseClass),
          attributes = propertyMap("attributes").asInstanceOf[mutable.Buffer[AttributePropertyInfo[Type, Class[_]]]],
          // Note, elements is the concatenation of both XmlElements and XmlElementRef - we make no distinction between the two
          elements =
            getElements(propertyMap("elements").asInstanceOf[mutable.Buffer[ElementPropertyInfo[Type, Class[_]]]])
            ++ getElementRefs(propertyMap("elementRefs").asInstanceOf[mutable.Buffer[RuntimeReferencePropertyInfo]])
          ,
          value =
            if(propertyMap("values").size == 1) propertyMap("values").asInstanceOf[mutable.Buffer[RuntimeValuePropertyInfo]].head
            else null
        )
      )
    )

    val result = generatedText.trim
    logger.info("Generated file :: \n" + result)

    result
  }

  def getElements(elements: mutable.Buffer[ElementPropertyInfo[Type, Class[_]]]): List[Element] = {
    elements
    .map(element => {
      new Element(
        name = element.getName.capitalize,
        isCollection = element.isCollection,
        references = {
          element.getTypes.asScala.map(elementType => elementType.getTagName.getLocalPart).toSet
        },
        dataType = {
          // XXX Force a call to getRawType, which isn't exposed via the JAXB API for some reason
          val declaredMethod: Method = element.getClass.getDeclaredMethod("getRawType")
          declaredMethod.setAccessible(true)
          val rawType: Type = declaredMethod.invoke(element).asInstanceOf[Type]
          getDataType(rawType)
        }
      )
    })
    .toList
  }

  def getElementRefs(elementRefs: mutable.Buffer[RuntimeReferencePropertyInfo]): List[Element] = {
    elementRefs
      .map(elementRef => {
        new Element (
          name = elementRef.getName.capitalize,
          isCollection = elementRef.isCollection,
          references = JavaConversions.asScalaSet(elementRef.getElements).map(elementRef => elementRef.getElementName.getLocalPart).toSet,
          // Grab the IndividualType ourselves, and wrap later if required
          dataType = getDataType(elementRef.getRawType)
        )
      })
      .toList
  }

  // TODO Perhaps we can't assume a raw type will always refer to the newly generated classes?
  def getDataType(parentType: Type): String = parentType match {
    // Create the full java type declaration, ie 'java.util.List<Foo, Bar, Baz>'
    case individualType: ParameterizedType => {
      def createString(paramType: ParameterizedType) = {
        paramType.getRawType.asInstanceOf[Class[_]].getSimpleName +
          paramType.getActualTypeArguments.map(getDataType).mkString("<", ", ", ">")
      }

      createString(individualType)
    }
    case individualType: Class[_] => individualType.getSimpleName
  }

  def generate(definition: GeneratorObject): String
}


