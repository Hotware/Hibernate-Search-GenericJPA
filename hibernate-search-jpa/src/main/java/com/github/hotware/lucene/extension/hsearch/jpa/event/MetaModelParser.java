package com.github.hotware.lucene.extension.hsearch.jpa.event;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.Attribute.PersistentAttributeType;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.ManagedType;
import javax.persistence.metamodel.Metamodel;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SingularAttribute;

import org.hibernate.search.annotations.ContainedIn;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.IndexedEmbedded;

/**
 * class used to parse the JPA annotations in respect to the Hibernate-Search
 * Index annotations. <br>
 * <br>
 * This class also has means to have accessors to the root entities of a
 * specific class. This could be used to propagate events up <br>
 * <br>
 * This could be used to propagate events up to the top entity, but
 * Hibernate-Search takes care of this via the @ContainedIn events, which
 * would is neat :). Either way, we still need this class to check whether the
 * classes are annotated properly (every entity has to know its parent via
 * 
 * @ContainedIn)
 * 
 * @author Martin Braun
 */
public class MetaModelParser {

	private final Map<Class<?>, Map<Class<?>, Function<Object, Object>>> rootParentAccessors = new HashMap<>();
	// only contains EntityTypes
	private final Map<Class<?>, ManagedType<?>> managedTypes = new HashMap<>();
	private final Map<Class<?>, Boolean> isRootType = new HashMap<>();
	private final Set<Class<?>> totalVisitedEntities = new HashSet<>();

	public Map<Class<?>, Map<Class<?>, Function<Object, Object>>> getRootParentAccessors() {
		return rootParentAccessors;
	}

	public Map<Class<?>, ManagedType<?>> getManagedTypes() {
		return managedTypes;
	}

	public Map<Class<?>, Boolean> getIsRootType() {
		return isRootType;
	}
	
	public Set<Class<?>> getIndexRelevantEntites() {
		return this.totalVisitedEntities;
	}

	public void parse(Metamodel metaModel) {
		this.rootParentAccessors.clear();
		this.managedTypes.clear();
		this.managedTypes.putAll(metaModel.getManagedTypes().stream()
				.filter((meta3) -> {
					return meta3 instanceof EntityType;
				}).collect(Collectors.toMap((meta) -> {
					return meta.getJavaType();
				}, (meta2) -> {
					return meta2;
				})));
		Set<EntityType<?>> emptyVisited = Collections.emptySet();
		this.totalVisitedEntities.clear();
		for (EntityType<?> curEntType : metaModel.getEntities()) {
			// we only consider Entities that are @Indexed here
			if (curEntType.getJavaType().isAnnotationPresent(Indexed.class)) {
				this.isRootType.put(curEntType.getJavaType(), true);
				Map<Class<? extends Annotation>, List<Attribute<?, ?>>> attributeForAnnotationType = this
						.buildAttributeForAnnotationType(curEntType);
				// and do the recursion
				this.doRecursion(attributeForAnnotationType, curEntType,
						emptyVisited);
			}
		}
	}

	public void parse(EntityType<?> curEntType, Class<?> cameFrom,
			Set<EntityType<?>> visited) {
		Map<Class<? extends Annotation>, List<Attribute<?, ?>>> attributeForAnnotationType = this
				.buildAttributeForAnnotationType(curEntType);

		Function<Object, Object> toRoot;
		// first of all, lets build the parentAccessor for this entity
		if (visited.size() > 0) {
			// don't do this for the first entity
			List<Attribute<?, ?>> cameFromAttributes = attributeForAnnotationType
					.getOrDefault(ContainedIn.class, new ArrayList<>())
					.stream()
					.filter((attribute) -> {
						Class<?> entityTypeClass = this
								.getEntityTypeClass(attribute);
						return entityTypeClass.equals(cameFrom);
					}).collect(Collectors.toList());
			if (cameFromAttributes.size() != 1) {
				throw new IllegalArgumentException(
						"entity: "
								+ curEntType.getJavaType()
								+ " has not exactly 1 @ContainedIn for each Index-parent specified");
			}
			Attribute<?, ?> toParentAttribute = cameFromAttributes.get(0);
			toRoot = (object) -> {
				Object parentOfThis = member(toParentAttribute.getJavaMember(),
						object);
				return parentOfThis;
			};
			this.getParentFunctionList(curEntType.getJavaType()).put(cameFrom,
					toRoot);
		}

		// and do the recursion
		this.doRecursion(attributeForAnnotationType, curEntType, visited);
	}

	private Map<Class<? extends Annotation>, List<Attribute<?, ?>>> buildAttributeForAnnotationType(
			EntityType<?> entType) {
		Map<Class<? extends Annotation>, List<Attribute<?, ?>>> attributeForAnnotationType = new HashMap<>();
		entType.getAttributes().forEach(
				(declared) -> {
					Member member = declared.getJavaMember();
					if (isAnnotationPresent(member, IndexedEmbedded.class)) {
						Class<? extends Annotation> annotationClass;
						annotationClass = IndexedEmbedded.class;
						List<Attribute<?, ?>> list = attributeForAnnotationType
								.computeIfAbsent(annotationClass, (key) -> {
									return new ArrayList<>();
								});
						list.add(declared);
					}
					if (isAnnotationPresent(member, ContainedIn.class)) {
						Class<? extends Annotation> annotationClass;
						annotationClass = ContainedIn.class;
						List<Attribute<?, ?>> list = attributeForAnnotationType
								.computeIfAbsent(annotationClass, (key) -> {
									return new ArrayList<>();
								});
						list.add(declared);
					}
				});
		return attributeForAnnotationType;
	}

	private void doRecursion(
			Map<Class<? extends Annotation>, List<Attribute<?, ?>>> attributeForAnnotationType,
			EntityType<?> entType, Set<EntityType<?>> visited) {
		// we don't change the original visited set.
		Set<EntityType<?>> newVisited = new HashSet<>(visited);
		// add the current entityType to the set
		newVisited.add(entType);
		this.totalVisitedEntities.add(entType.getJavaType());
		// we don't want to visit already visited entities
		// this should be okay to do, as cycles don't matter
		// as long as we start from the original
		attributeForAnnotationType
				.getOrDefault(IndexedEmbedded.class, new ArrayList<>())
				.stream()
				.filter((attribute) -> {
					Class<?> entityTypeClass = this
							.getEntityTypeClass(attribute);
					boolean notVisited = !visited.contains(this.managedTypes
							.get(entityTypeClass));
					PersistentAttributeType attrType = attribute
							.getPersistentAttributeType();
					boolean otherEndIsEntity = attrType != PersistentAttributeType.BASIC
							&& attrType != PersistentAttributeType.EMBEDDED;
					if (attrType == PersistentAttributeType.ELEMENT_COLLECTION) {
						throw new UnsupportedOperationException(
								"Element Collections are not allowed as with plain JPA "
										+ "as we haven't reliably proved, how to get the "
										+ "events to update our index!");
					}
					// Collections get updated in the owning entity :)
					// TODO: we should still test whether MANY_TO_MANY
					// are fine as well, but they should
					// if (attrType == PersistentAttributeType.MANY_TO_MANY) {
					// throw new UnsupportedOperationException(
					// "MANY_TO_MANY is not allowed as with plain JPA "
					// +
					// "we cannot reliably get the events to update our index!"
					// + " Please map the Bridge table itself. "
					// +
					// "Btw.: Map all your Bridge tables when using this class!");
					// }
					return notVisited && otherEndIsEntity;
				})
				.forEach(
						(attribute) -> {
							Class<?> entityTypeClass = this
									.getEntityTypeClass(attribute);
							this.parse((EntityType<?>) this.managedTypes
									.get(entityTypeClass), entType
									.getJavaType(), newVisited);
						});
	}

	private Map<Class<?>, Function<Object, Object>> getParentFunctionList(
			Class<?> clazz) {
		return this.rootParentAccessors.computeIfAbsent(clazz, (key) -> {
			return new HashMap<>();
		});
	}

	private Class<?> getEntityTypeClass(Attribute<?, ?> attribute) {
		Class<?> entityTypeClass;
		if (attribute instanceof PluralAttribute<?, ?, ?>) {
			entityTypeClass = (((PluralAttribute<?, ?, ?>) attribute)
					.getElementType().getJavaType());
		} else if (attribute instanceof SingularAttribute<?, ?>) {
			entityTypeClass = (((SingularAttribute<?, ?>) attribute).getType()
					.getJavaType());
		} else {
			throw new AssertionError("attributes have to either be "
					+ "instanceof PluralAttribute or SingularAttribute "
					+ "at this point");
		}
		return entityTypeClass;
	}

	public static boolean isAnnotationPresent(Member member,
			Class<? extends Annotation> annotationClass) {
		boolean ret = false;
		if (member instanceof Method) {
			Method method = (Method) member;
			ret = method.isAnnotationPresent(annotationClass);
		} else if (member instanceof Field) {
			Field field = (Field) member;
			ret = field.isAnnotationPresent(annotationClass);
		} else {
			throw new AssertionError("member should either be Field or Member");
		}
		return ret;
	}

	public static Object member(Member member, Object object) {
		try {
			Object ret;
			if (member instanceof Method) {
				Method method = (Method) member;
				ret = method.invoke(object);
			} else if (member instanceof Field) {
				Field field = (Field) member;
				ret = field.get(object);
			} else {
				throw new AssertionError(
						"member should either be Field or Member");
			}
			return ret;
		} catch (IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}

}
