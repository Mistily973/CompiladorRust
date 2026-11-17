struct Vacio;

struct Rect {
    ancho: f64,
    alto: f64,
}

struct Caja {
    r: Rect,
    id: u8,
    activo: bool,
}

fn fact(n: u64) -> u64 {
    if n <= 1 { 1 } else { n * fact(n - 1) }
}

fn area(r: &Rect) -> f64 {
    r.ancho * r.alto
}

fn escala(r: &mut Rect, k: f64) {
    r.ancho *= k;
    r.alto = r.alto * k;
}

fn primero(v: &Vec<i32>) -> i32 {
    v[0]
}

fn main() {
    let x = 1;
    let x = "ahora texto";
    let y;
    if x.len() > 2 { y = 1; } else { y = 2; }
    println!("{}", y);
    let mut r = Rect { ancho: 2.0, alto: 3.0 };
    escala(&mut r, 2.0);
    let a = area(&r);
    let c = Caja { r: Rect { ancho: 1.0, alto: 1.0 }, id: 7, activo: true };
    println!("{} {} {}", a, c.id, c.activo);
    let v = vec![10, 20, 30];
    let p = primero(&v);
    let mut suma = 0;
    for e in &v {
        suma += *e;
    }
    for e in v.iter() {
        suma += e;
    }
    let mut i = 0usize;
    while i < v.len() {
        suma = suma + v[i];
        i += 1;
    }
    let t = (1, "dos", 3.0);
    let (u, s, f) = t;
    println!("{} {} {} {}", t.0, t.1, u, s);
    let mayor = std::cmp::max(3, 9);
    let f2 = f * 2.0 + (mayor as f64);
    let ch = 'x';
    let up = ch.to_ascii_uppercase();
    let texto = String::from("abc");
    let n = texto.len() as i32 + 1;
    let mut cont = 0;
    loop {
        cont += 1;
        if cont == 3 { break; }
    }
    let etiqueta = match (cont, up) {
        (3, 'X') => "tres",
        (_, _) => "otro",
    };
    let mut total: i64 = 0;
    total += 5;
    let neg = -total;
    let b = !true && (n > 0 || p == 10);
    let ff = fact(5);
    let big: u64 = ff + 1;
    let valor = v.get(0);
    let raiz = (2.0f64).sqrt();
    let redondeo = raiz.powi(2) + 1.5;
    let z = Vacio;
    {
        let interno = 5;
        println!("{}", interno);
    }
    println!("{} {} {} {} {} {}", etiqueta, neg, b, big, redondeo, f2);
    println!("{{}} literal {:?} {:.2}", valor, raiz);
}
