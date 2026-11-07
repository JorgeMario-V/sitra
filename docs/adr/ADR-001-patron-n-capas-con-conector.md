# ADR-001: Patrón N-Capas con un conector intercambiable para el núcleo legado

## Estatus
Aceptada

## Fecha
2026-10-24

## Contexto
SITRA debe exponer consulta de saldo y transferencias de un núcleo legado de 20 años, sin
documentación, que no se puede modificar y del que no existe ambiente de pruebas. La cooperativa
no tiene personal técnico: quien herede el sistema debe entenderlo sin ayuda.

Atributos en juego: **AC-03 Mantenibilidad** (modularidad y capacidad de ser probado) y
**AC-02 Fiabilidad**. Restricciones: núcleo intocable, sin ambiente de pruebas, cero personal
técnico, cero presupuesto.

Alternativas consideradas:
- **MVC monolítico:** el controlador hablaría directo con el núcleo y la base de datos.
- **Hexagonal (puertos y adaptadores):** máxima independencia, pero más clases, interfaces y conceptos.
- **Microservicios:** varios servicios que operar y monitorear.
- **N-Capas:** seguridad → presentación → negocio → datos.

## Decisión
Decidimos usar N-Capas (seguridad, presentación, negocio y datos) y que el núcleo se use solo a
través de la interfaz `NucleoConector`, con dos implementaciones que se cambian por configuración
(`sitra.nucleo.modo`): `NucleoSimuladoConector` para pruebas y `NucleoLegadoConector` para
producción, porque AC-03 exige poder probar todo sin el núcleo real y la restricción de cero
personal técnico exige la estructura más estándar posible.

Se descarta MVC porque no deja dónde poner el simulador sin tocar código de negocio. Se descarta
Hexagonal completo porque su costo en clases y curva de aprendizaje contradice la restricción de
cero personal; de él solo se toma la interfaz del conector. Se descarta Microservicios porque
exige personal y presupuesto que no existen.

## Consecuencias / Trade-offs
- **Positivas:** cualquier desarrollador Java reconoce la estructura (controller, service,
  repository, model); el servicio no sabe si habla con el núcleo real o con el simulador; cambiar
  la integración toca una sola clase y un valor de configuración.
- **Negativas:** más código que un MVC simple (servicios y repositorios separados). El dominio no
  queda totalmente aislado de Spring, como sí lo haría en Hexagonal.
- **Deuda técnica asumida:** `NucleoLegadoConector` responde "no disponible" porque el protocolo
  del núcleo está por confirmar con el cliente. Condición para revisarla: cuando el cliente
  confirme cómo expone sus datos el núcleo (archivo, base de datos o servicio), se implementa solo
  esa clase, con una espera máxima de 5 s (AC-02).
