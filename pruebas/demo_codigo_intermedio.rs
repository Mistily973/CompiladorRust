fn promedio(a: i32, b: i32, c: i32) -> i32 {
    let suma = a + b + c;
    let resultado = suma / 3;
    return resultado;
}

fn area_trapecio(base_mayor: f64, base_menor: f64, altura: f64) -> f64 {
    let suma_bases = base_mayor + base_menor;
    let area = (suma_bases * altura) / 2.0;
    area
}

fn formula_general(a: f64, b: f64, c: f64) -> f64 {
    let discriminante = b * b - 4.0 * a * c;
    let numerador = -b + discriminante;
    let raiz = numerador / (2.0 * a);
    raiz
}

fn main() {
    let x: i32 = 10;
    let y: i32 = 4;
    let mut total: i32 = 0;

    total = x * y + x - y % 3;

    let p = promedio(x, y, total);
    let area = area_trapecio(5.0, 3.0, 2.0);
    let raiz = formula_general(1.0, -3.0, 2.0);

    if x + y > 10 {
        let bono = x * 2 + y;
        println!("Bono: {}", bono);
    }

    println!("Promedio: {}", p);
    println!("Area: {}", area);
    println!("Raiz: {}", raiz);
}
