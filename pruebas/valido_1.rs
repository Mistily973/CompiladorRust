struct Persona {
    nombre: String,
    edad: u32,
    altura: f64,
}

fn suma(a: i32, b: i32) -> i32 {
    a + b
}

fn saludar(nombre: &str, veces: u32) {
    let mut i = 0;
    while i < veces {
        println!("Hola {}", nombre);
        i += 1;
    }
}

fn duplicar(x: &mut i32) {
    *x = *x * 2;
}

fn main() {
    let x = 5;
    let mut y: i32 = 10;
    y = suma(x, y);
    y += 1;
    let z: f64 = 3.5;
    let w = z * 2.0;
    let nombre = String::from("Ana");
    let mut p = Persona { nombre: nombre, edad: 30, altura: 1.7 };
    p.edad = 31;
    p.altura += 0.1;
    saludar("mundo", 2);
    let mut v = Vec::new();
    v.push(1);
    v.push(2);
    let n = v.len();
    let mut arr = [1, 2, 3];
    arr[0] = 10;
    duplicar(&mut y);
    let (a, b) = (1, 2.5);
    let r = if y > 3 { 1 } else { 2 };
    let t = loop { break 7; };
    for i in 0..3 {
        println!("{} {}", i, x);
    }
    let mensaje = format!("{} tiene {} años ({})", p.nombre, p.edad, w);
    println!("{mensaje} {n} {a} {b} {r} {t}");
    let c = 'a';
    let letra = c as u8;
    let m = match x { 1 => 10, 2..=5 => 20, _ => 0 };
    let mut s = String::new();
    s.push_str("hola");
    s += " mundo";
    println!("{} {} {}", letra, m, s);
}
