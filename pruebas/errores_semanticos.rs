struct Punto {
    x: i32,
    y: i32,
}

fn suma(a: i32, b: i32) -> i32 {
    a + b
}

fn saluda(nombre: &str, edad: u32) {
    println!("{} {}", nombre, edad);
}

fn incrementa(n: &mut i32) {
    *n += 1;
}

fn main() {
    let x = 5;
    x = 6;
    let mut y: i32 = 3.5;
    let z: f64 = 10;
    let mut s: String = "hola";
    let mut w = 1;
    w = "texto";
    w += 2.5;
    q = 4;
    let r = suma(1);
    let r2 = suma(1, 2, 3);
    let r3 = suma(1, 2.5);
    let r4 = suma("a", 2);
    saluda(5, 3);
    saluda("Ana", -1.0);
    noexiste(1);
    incrementa(&mut x);
    incrementa(y);
    let p = Punto { x: 1, y: 2 };
    p.x = 10;
    let mut p2 = Punto { x: 1 };
    p2.z = 3;
    let mut q2 = Punto { x: 1, y: 2.0 };
    let v = vec![1, 2, 3];
    v.push(4);
    let len: i32 = v.len();
    let mut u = vec![1];
    u.push("a");
    u.push(1, 2);
    println!("{} {}", x);
    println!("{}", x, y);
    println!("{}", p);
    let flag: bool = 1;
    if x { println!("si"); }
    let m = x + 2.5;
    let n = true + 1;
    let k = "a" - "b";
    let u2;
    println!("{}", u2);
    let (a, b, c) = (1, 2);
    let d: Fantasma = 1;
    break;
    let e: i32 = suma(1, 2) as f64;
    let mut idx: i32 = 0;
    let arr = [1, 2, 3];
    let f = arr[idx];
    let g = if x > 1 { 1 } else { "no" };
}
