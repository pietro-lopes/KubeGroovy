TestRuntime.check('syntax.closures') {
	def add = { a, b -> a + b }
	TestRuntime.assertThat(add(3, 4) == 7).isTrue()
}

TestRuntime.check('syntax.multiple_assignment') {
	def values = [1, 2]
	def (a, b) = values
	TestRuntime.assertThat(a == 1 && b == 2).isTrue()
}

TestRuntime.check('syntax.gstrings') {
	def name = 'world'
	TestRuntime.assertThat("hello ${name}").isEqualTo('hello world')
}

TestRuntime.check('syntax.for_in') {
	def total = 0
	for (n in [1, 2, 3, 4]) total += n
	TestRuntime.assertThat(total == 10).isTrue()
}

TestRuntime.check('syntax.collection_pipelines') {
	def result = [1, 2, 3, 4].findAll { it % 2 == 0 }.collect { it * 10 }
	TestRuntime.assertThat(result.join(',')).isEqualTo('20,40')
}

TestRuntime.check('syntax.map_and_set') {
	def map = [a: 1]
	def set = [1, 1, 2] as Set
	TestRuntime.assertThat(map.a == 1 && set.size() == 2).isTrue()
}

TestRuntime.check('syntax.default_parameters') {
	def add = { a, b = 3 -> a + b }
	TestRuntime.assertThat(add(4) == 7).isTrue()
}

TestRuntime.check('syntax.spread_arguments') {
	def sum = { Object... xs -> xs.sum(0) }
	TestRuntime.assertThat(sum(*[1, 2, 3], 4) == 10).isTrue()
}

TestRuntime.check('syntax.normal_cast') {
	def text = 'hello' as String
	TestRuntime.assertThat(text).isEqualTo('hello')
}
