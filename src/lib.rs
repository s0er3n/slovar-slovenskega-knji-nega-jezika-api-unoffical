use reqwest::header::USER_AGENT;
use serde::Serialize;
use std::collections::{HashMap, HashSet};
use tl::{
    queryselector::iterable::QueryIterable, HTMLTag, Node::Tag, NodeHandle, ParserOptions, VDom,
};
use worker::*;

mod utils;

fn log_request(req: &Request) {
    console_log!(
        "{} - [{}], located at: {:?}, within: {}",
        Date::now().to_string(),
        req.path(),
        req.cf().coordinates().unwrap_or_default(),
        req.cf().region().unwrap_or_else(|| "unknown region".into())
    );
    console_log!("header = {:?}", &req.headers());
}
async fn get_word(target: &str) -> String {
    let client = reqwest::Client::new();
    let resp_txt = client
        .get(format!(
            "https://www.fran.si/iskanje?View=1&Query={}",
            target
        ))
        .send()
        .await
        .unwrap()
        .text()
        .await
        .unwrap();

    let dom: VDom = tl::parse(&resp_txt, ParserOptions::default()).unwrap();

    let parser = dom.parser();

    let description = dom
        .get_elements_by_class_name("entry-content")
        .next()
        .unwrap();

    let description: String = NodeHandle::get(&description, parser)
        .unwrap()
        .inner_text(parser)
        .into();

    return description;
}

#[event(fetch)]
pub async fn main(req: Request, env: Env, _ctx: worker::Context) -> Result<Response> {
    log_request(&req);

    // Optionally, get more helpful error messages written to the console in the case of a panic.
    utils::set_panic_hook();

    // Optionally, use the Router to handle matching endpoints, use ":name" placeholders, or "*name"
    // catch-alls to match on specific patterns. Alternatively, use `Router::with_data(D)` to
    // provide arbitrary data that will be accessible in each route via the `ctx.data()` method.
    let router = Router::new();

    // Add as many routes as your Worker needs! Each route will get a `Request` for handling HTTP
    // functionality and a `RouteContext` which you can use to  and get route parameters and
    // Environment bindings like KV Stores, Durable Objects, Secrets, and Variables.
    router
        .get_async("/:word", |mut req, ctx| async move {
            if let Some(name) = ctx.param("word") {
                let cors = worker::Cors::new()
                    .with_origins(["*"])
                    .with_methods([Method::Get]);
                let response = get_word(name).await;
                return Response::from_json(&response)?.with_cors(&cors);
            }

            Response::error("Bad Request", 400)
        })
        .run(req, env)
        .await
}
