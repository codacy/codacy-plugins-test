package com.codacy.plugins.api.results

object Category {

  // TS-682: Return only categories that are enabled in the production database
  val allowedCategories = Pattern.Category.values
    .filterNot(_ == Pattern.Category.Duplication)

}
