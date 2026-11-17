fn calcular_area() -> i32 {
    let ancho = 4;
    let alto = 5;
    let area = ancho * alto;
    let margen = 2 + 3;
    area + margen
}

fn formula(x: i32) -> i32 {
    let a = 2;
    let b = 3;
    let c = 4;
    let constante = (a + b) * c;
    x * (10 - 4) + constante
}

fn promedio() -> f64 {
    let total = 10 as f64;
    let cantidad = 4 as f64;
    total / cantidad
}

fn potencias() -> i32 {
    let base = 2 + 1;
    let exp = 4;
    base.pow(exp as u32) + (2 * 3).pow(2)
}

fn main() {
    let modo = 5 > 3;
    let limite = 100 / 4 + 5 % 3;
    let mut suma = 0;
    let mut i = 0;
    while i < limite {
        suma += i * (2 + 2);
        i += 1;
    }

    if modo && 1 < 2 {
        println!("modo activo");
    }

    println!("area = {}", calcular_area());
    println!("formula = {}", formula(7));
    println!("promedio = {}", promedio());
    println!("potencias = {}", potencias());
    println!("suma = {}", suma);
}
