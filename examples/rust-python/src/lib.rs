use pyo3::prelude::*;
#[pyfunction]
fn answer() -> i32 {42}
#[pymodule]
fn appforge_native(module: &Bound<'_, PyModule>) -> PyResult<()> {
 module.add_function(wrap_pyfunction!(answer, module)?)?; Ok(())
}
