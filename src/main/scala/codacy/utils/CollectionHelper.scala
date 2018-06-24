package codacy.utils

final case class DiffResult[T1, T2](newObjects: Seq[T1], deletedObjects: Seq[T2])

class CollectionHelper[T1, T2, K](currentObjects: Seq[T1], oldObjects: Seq[T2])(currentObjectsKeyMapper: T1 => K,
                                                                                oldObjectsKeyMapper: T2 => K) {

  def fastDiff: DiffResult[T1, T2] = {
    val currentObjectKeys = currentObjects.map(currentObjectsKeyMapper)
    val oldObjectKeys = oldObjects.map(oldObjectsKeyMapper)

    val newObjectKeys = currentObjectKeys.diff(oldObjectKeys)
    val deletedObjectsKeys = oldObjectKeys.diff(currentObjectKeys)

    val currentObjectsMap = oldObjects.map(c => (oldObjectsKeyMapper(c), c)).toMap
    val oldObjectsMap = currentObjects.map(o => (currentObjectsKeyMapper(o), o)).toMap

    val newObjects = newObjectKeys.flatMap(oldObjectsMap.get)
    val deletedObjects = deletedObjectsKeys.flatMap(currentObjectsMap.get)

    DiffResult(newObjects, deletedObjects)
  }

}
